(ns aging-session.cookie-test
  (:require [clojure.test :refer :all]
            [aging-session.cookie :refer :all]))


(deftest crypto-basic-functionality
  (testing "can create keys"
    (is (= 2 (count (get-crypto-keys)))))
  (testing "key creation/retrieval is atomic"             ; this isn't proof, just a best-effort check
    (is (= 1 (count (into #{} (doall (pmap #(do %& (map base64-encode (get-crypto-keys)))
                                           (range 100))))))))
  (testing "encryption roundtrip"
    (let [data {:some :data}
          key (first (get-crypto-keys))
          [iv enc-data] (encrypt key data)]
      (is (= data (decrypt iv enc-data key)))))
  (testing "cookie roundtrip"
    (let [id 1234567]
      (is (= id (get-id (bake-cookie id)))))))