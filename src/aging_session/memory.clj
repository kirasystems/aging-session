(ns aging-session.memory
  "In-memory session storage with mortality."
  (:require [ring.middleware.session.store :refer :all]
            [ring.util.codec :refer [url-encode url-decode]]
            [buddy.core.crypto :as crypto]
            [buddy.core.mac :as mac]
            [buddy.core.nonce :as nonce]
            [clj-time.core :as time]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [taoensso.nippy :as nippy])
  (:import  [org.apache.commons.codec.binary Base64]))

(defrecord SessionEntry [timestamp value])

;; Base64 helpers
(defn- string->bytes
  "Convert UTF-8 string to bytes."
  ^bytes [^String s]
  (.getBytes s "UTF-8"))

(defn base64-encode [b]
  (Base64/encodeBase64String b))

(defn base64-decode
  ^bytes [^String s]
  (Base64/decodeBase64 (string->bytes s)))

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

(def ^:private internals-atom (atom {:enc-key nil
                                     :mac-key nil
                                     :counter 0}))

(defn get-crypto-keys
  []
  (let [{enc-key :enc-key
         mac-key :mac-key} @internals-atom]
    (if (and enc-key mac-key)                               ; if keys are present - use them
      [enc-key mac-key]
      (do                                                   ; otherwise generate new keys and attempt to set them
        (swap! internals-atom update-in [:enc-key] #(if-not % (nonce/random-bytes 32) %))
        (swap! internals-atom update-in [:mac-key] #(if-not % (nonce/random-bytes 128) %))
        (get-crypto-keys)))))                               ; retrieve the keys again, as something else may have set them in the meantime

(defn decrypt
  [iv key data]
  (try
    (nippy/thaw (crypto/decrypt-cbc (crypto/block-cipher :aes :cbc) data key iv))
    (catch Exception e
      (log/error "Error decrypting cookie: " e)
      nil)))

(defn encrypt
  [key data]
  (let [iv (nonce/random-bytes 16)]
    [iv (crypto/encrypt-cbc (crypto/block-cipher :aes :cbc) (nippy/freeze data) key iv)]))

(defn verify-and-decrypt-cookie
  [url-cookie]
  (let [cookie (some-> url-cookie
                       url-decode)
        [enc-key mac-key] (get-crypto-keys)
        [iv enc-cookie hmac] (string/split (or cookie "") #"\." 3)
        enc-cookie (and hmac
                        (mac/verify (str iv enc-cookie) (base64-decode hmac) {:key mac-key :alg :hmac+sha512})
                        enc-cookie)
        cookie (and enc-cookie
                    (decrypt (base64-decode iv) enc-key (base64-decode enc-cookie)))
        iat (and cookie
                 (:iat cookie))]
    (when (and cookie iat
               (time/within? (time/interval (time/minus (time/now)
                                                        (time/days 3))
                                            (time/now))
                             iat))
      cookie)))

(defn encrypt-and-hmac-cookie
  [session-id]
  (let [[enc-key mac-key] (get-crypto-keys)
        iat (time/now)
        [iv enc-cookie] (encrypt enc-key {:id session-id :iat iat})
        ivb64 (base64-encode iv)
        enc-cookieb64 (base64-encode enc-cookie)
        hmac (mac/hash (str ivb64 enc-cookieb64) {:key mac-key :alg :hmac+sha512})
        hmacb64 (base64-encode hmac)]
    (some-> (str ivb64 "." enc-cookieb64 "." hmacb64)
            url-encode)))


(defprotocol AgingStore
  (read-timestamp [store key]
                  "Read a session from the store and return its timestamp. If no key exists, returns nil."))

(defrecord MemoryAgingStore [session-map refresh-on-write refresh-on-read req-count req-limit event-fns]
  AgingStore
  (read-timestamp [_ key]
    (get-in @session-map [key :timestamp]))

  SessionStore
  (read-session [_ cookie]
    (let [key (:id (verify-and-decrypt-cookie cookie))]
      (swap! session-map sweep-entry event-fns key)
      (when (and refresh-on-read (contains? @session-map key))
        (swap! session-map assoc-in [key :timestamp] (now)))
      (get-in @session-map [key :value] {})))

  (write-session [_ cookie {:keys [bump-session?] :as data}]
    (let [decrypted-cookie (verify-and-decrypt-cookie cookie)
          key (or (:id decrypted-cookie)
                  (:counter (swap! internals-atom update-in [:counter] inc)))
          session (cond-> data
                    bump-session? (assoc :last-access (System/currentTimeMillis))
                    true (dissoc :bump-session?))]
      (swap! req-count inc)	  ; Increase the request count
      (if refresh-on-write    ; Write key and and update timestamp.
        (swap! session-map assoc key (new-entry session))
        (swap! session-map write-entry key session))
      (if decrypted-cookie
        cookie
        (encrypt-and-hmac-cookie key))))

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

