(ns aging-session.memory
  "In-memory session storage with mortality."
  (:require [aging-session.crypto :as crypto]
            [ring.middleware.session.store :refer :all]
            [ring.util.codec :refer [url-encode url-decode]]))

(defrecord SessionEntry [timestamp value])

(defn- now
  "Return the current time in milliseconds."
  []
  (System/currentTimeMillis))

(defn- new-entry
  "Create a new session entry for data."
  [data]
  (SessionEntry. (now) data))

(defn- write-entry
  "Write over an existing entry. If timestamp is missing, recreate."
  [session-map key data]
  (if (get-in session-map [key :timestamp])
    (assoc-in session-map [key :value] data)
    (assoc session-map key (new-entry data))))

(defn- update-entry
  "Update a session entry based on event functions."
  [event-fns ts [k v]]
  (loop [entry v, fns (seq event-fns)]
    (if (and entry fns)
      (recur ((first fns) ts entry) (next fns))
      (if entry [k entry]))))

(defn- sweep-session
  "Sweep the session and run all session functions."
  [session-map event-fns]
  (let [ts (now)]
    (into {} (keep #(update-entry event-fns ts %) session-map))))

(defn- sweep-entry
  "Sweep a single entry."
  [session-map event-fns key]
  (if-let [[_ entry] (update-entry event-fns (now) [key (get session-map key)])]
    (assoc session-map key entry)
    (dissoc session-map key)))

(defprotocol AgingStore
  (read-timestamp [store key]
                  "Read a session from the store and return its timestamp. If no key exists, returns nil."))

(defrecord MemoryAgingStore [session-map refresh-on-write refresh-on-read req-count req-limit event-fns]
  AgingStore
  (read-timestamp [_ key]
    (get-in @session-map [key :timestamp]))

  SessionStore
  (read-session [_ cookie]
    (let [key (:id (crypto/verify-and-decrypt-cookie cookie))]
      (swap! session-map sweep-entry event-fns key)
      (when (and refresh-on-read (contains? @session-map key))
        (swap! session-map assoc-in [key :timestamp] (now)))
      (get-in @session-map [key :value] {})))

  (write-session [_ cookie {:keys [bump-session?] :as data}]
    (let [decrypted-cookie (crypto/verify-and-decrypt-cookie cookie)
          key (or (:id decrypted-cookie)
                  (:counter (crypto/next-session!)))
          session (cond-> data
                    bump-session? (assoc :last-access (System/currentTimeMillis))
                    true (dissoc :bump-session?))]
      (swap! req-count inc)	  ; Increase the request count
      (if refresh-on-write    ; Write key and and update timestamp.
        (swap! session-map assoc key (new-entry session))
        (swap! session-map write-entry key session))
      (if decrypted-cookie
        cookie
        (crypto/encrypt-and-hmac-cookie key))))

  (delete-session [_ key]
    (swap! session-map dissoc key)
    nil))

(defn sweeper-thread
  "Sweeper thread that watches the session and cleans it."
  [{:keys [req-count req-limit session-map event-fns]} sweep-delay]
  (loop []
    (when (>= @req-count req-limit)
      (swap! session-map sweep-session event-fns)
      (reset! req-count 0))
    (. Thread (sleep sweep-delay))  ;; sleep for 30s
    (recur)))

(defn in-thread
  "Run a function in a thread."
  [afn]
  (.start (Thread. afn)))

(defn aging-memory-store
  "Creates an in-memory session storage engine."
  [& opts]
  (let [{:keys [session-atom refresh-on-write refresh-on-read sweep-every sweep-delay events]
         :or   {session-atom     (atom {})
                refresh-on-write false
                refresh-on-read  false
                sweep-every      200
                sweep-delay      30000
                events           []}} opts
        counter-atom (atom 0)
        store        (MemoryAgingStore. session-atom refresh-on-write refresh-on-read counter-atom sweep-every events)]
    (in-thread #(sweeper-thread store sweep-delay))
    store))

