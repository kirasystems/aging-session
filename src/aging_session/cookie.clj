(ns aging-session.cookie
  (:require [buddy.core.crypto :as crypto]
            [buddy.core.mac :as mac]
            [buddy.core.nonce :as nonce]
            [clj-time.core :as time]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [taoensso.nippy :as nippy]
            [ring.util.codec :refer [url-encode url-decode]])
  (:import [org.apache.commons.codec.binary Base64]))

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

(def ^:private internals-atom (atom {:enc-key nil
                                     :mac-key nil
                                     :counter 0}))

(defn next-session!
  []
  (swap! internals-atom update-in [:counter] inc))

(defn get-crypto-keys
  []
  (let [{enc-key :enc-key
         mac-key :mac-key} @internals-atom]
    (if (and enc-key mac-key)                               ; if keys are initialized - use them
      [enc-key mac-key]
      (do                                                   ; otherwise generate new keys and attempt to set them
        (swap! internals-atom update-in [:enc-key] #(if-not % (nonce/random-bytes 32) %))
        (swap! internals-atom update-in [:mac-key] #(if-not % (nonce/random-bytes 128) %))
        (get-crypto-keys)))))                               ; retrieve the keys again, as something else may have set them in the meantime

(defn encrypt
  [key data]
  (let [iv (nonce/random-bytes 16)]
    [iv (crypto/encrypt-cbc (crypto/block-cipher :aes :cbc) (nippy/freeze data) key iv)]))

(defn decrypt
  "Attempt decryption of a payload given the corresponding IV and an encryption key.

  Arguments:
    - iv   - initialization vector provided with the encrypted payload
    - data - ciphertext
    - key  - decryption key"
  [iv data key]
  (try
    (nippy/thaw (crypto/decrypt-cbc (crypto/block-cipher :aes :cbc) data key iv))
    (catch Exception e
      (log/error "Error decrypting cookie: " e)
      nil)))

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

(defn- verify-and-return-data
  [iv data key hmac]
  (when (mac/verify (str iv data) (base64-decode hmac) {:key key :alg :hmac+sha512})
    data))

(defn- not-expired?
  [{iat :iat}]
  (if iat
    (time/within? (time/interval (time/minus (time/now)
                                             (time/days 3))
                                 (time/now))
                  iat)
    false))

(defn verify-and-decrypt-cookie
  [url-cookie]
  (let [[iv enc-cookie hmac] (some-> url-cookie
                                     url-decode
                                     (or "")
                                     (string/split #"\." 3))
        [enc-key mac-key] (get-crypto-keys)
        cookie (some->> hmac
                        (verify-and-return-data iv enc-cookie mac-key)
                        (#(decrypt (base64-decode iv) (base64-decode %) enc-key)))]
    (when (not-expired? cookie)
      cookie)))
