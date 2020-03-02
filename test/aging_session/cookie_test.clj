(ns aging-session.cookie-test
  (:require [clojure.test :refer :all]
            [aging-session.cookie :refer :all]))


(deftest crypto-basic-functionality
  (testing "can create keys"
    (is (= 2 (count (get-crypto-keys)))))
  (testing "key creation/retrieval is atomic"               ; this isn't proof, just a best-effort check
    (is (= 1 (count (into #{} (doall (pmap #(do %& (map base64-encode (get-crypto-keys)))
                                           (range 100))))))))
  (testing "encryption roundtrip"
    (let [data {:some :data}
          key (first (get-crypto-keys))
          [iv enc-data] (encrypt key data)]
      (is (= data (decrypt iv enc-data key)))))
  (let [id 1234567
        cookie (bake-cookie id)]
    (testing "cookie roundtrip"
      (is (= id (get-id cookie))))
    (testing "Corrupt IV results in nil"
      (is (nil? (get-id (str "badpre" (subs cookie 6))))))
    (testing "Corrupt MAC results in nil"
      (is (nil? (get-id (str (subs cookie 0 (- (count cookie) 6)) "badsuf")))))
    (testing "Corrupt payload results in nil"
      (is (nil? (get-id (str (subs cookie 0 40) "badpay" (subs cookie 46))))))))