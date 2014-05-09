(ns aging-session.memory_test
  (:require
    [clojure.test :refer :all]
    [ring.middleware.session.store :refer :all]
    [aging-session.memory :refer :all]))

(deftest basic-read-empty
  (testing "Test session reads."
    (let [as (aging-memory-store)]
      (is (= (read-session as "mykey") {})))))

(deftest basic-write
  (testing "Test session writes and reads."
    (let [as (aging-memory-store)]
      (write-session as "mykey" {:a 1})
      (is (= (read-session as "mykey") {:a 1}))
      (write-session as "mykey" {:a 2})
      (is (= (read-session as "mykey") {:a 2})))))

(deftest basic-delete
  (testing "Test session delete."
    (let [as (aging-memory-store)]
      (write-session as "mykey" {:a 1})
      (delete-session as "mykey")
      (is (= (read-session as "mykey") {})))))

(deftest timestamp-on-creation
  (testing "Test the behaviour where each entry's timestamp is set only on session creation."
    (let [as (aging-memory-store)]
      (write-session as "mykey" {:foo 1})
      (let [ts1 (read-timestamp as "mykey")]
        (is (integer? ts1))
        (write-session as "mykey" {:foo 2})
        (is (= ts1 (read-timestamp as "mykey")))
        (is (= (read-session as "mykey") {:foo 2}))))))

(deftest timestamp-on-creation
  (testing "Test the behaviour where each entry's timestamp is refreshed on write."
    (let [as (aging-memory-store :refresh-on-write true)]
      (write-session as "mykey" {:foo 1})
      (let [ts1 (read-timestamp as "mykey")]
        (. Thread (sleep 10))
        (write-session as "mykey" {:foo 2})
        (is (not (= ts1 (read-timestamp as "mykey"))))
        (is (= (read-session as "mykey") {:foo 2}))))))