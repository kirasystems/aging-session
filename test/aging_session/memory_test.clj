(ns aging-session.memory_test
  (:require
    [clojure.test :refer :all]
    [ring.middleware.session.store :refer :all]
    [aging-session.cookie :refer :all]
    [aging-session.memory :refer :all]))

(deftest basic-read-empty
  (let [as (aging-memory-store)
        cookie (write-session as nil {:some :thing})]
    (testing "Invalid session is empty."
      (is (= (read-session as "invalid-cookie") {})))
    (testing "Session with corrupt IV is empty"
      (is (= (read-session as (str "badpre" (subs cookie 6))) {})))
    (testing "Session with corrupt MAC is empty"
      (is (= (read-session as (str (subs cookie 0 (- (count cookie) 6)) "badsuf")) {})))
    (testing "Session with corrupt payload is empty"
      (is (= (read-session as (str (subs cookie 0 40) "badpay" (subs cookie 46))) {})))))

(deftest basic-write
  (testing "Test session writes and reads."
    (let [as (aging-memory-store)
          cookie (write-session as nil {:a 1})]
      (is (= (read-session as cookie) {:a 1}))
      (write-session as cookie {:a 2})
      (is (= (read-session as cookie) {:a 2})))))

(deftest basic-delete
  (testing "Test session delete."
    (let [as (aging-memory-store)
          cookie (write-session as nil {:a 1})]
      (is (= 1 (:a (read-session as cookie))))
      (delete-session as (get-id cookie))
      (is (= (read-session as cookie) {})))))

(deftest timestamp-on-creation
  (testing "Test the behaviour where each entry's timestamp is set only on session creation."
    (let [as (aging-memory-store)
          cookie (write-session as nil {:a 1})
          id (get-id cookie)]
      (let [ts1 (read-timestamp as id)]
        (is (integer? ts1))
        (write-session as cookie {:foo 2})
        (is (= ts1 (read-timestamp as id)))
        (is (= (read-session as cookie) {:foo 2}))))))

(deftest timestamp-on-write
  (testing "Test the behaviour where each entry's timestamp is refreshed on write."
    (let [as (aging-memory-store :refresh-on-write true)
          cookie (write-session as nil {:a 1})
          id (get-id cookie)]
      (let [ts1 (read-timestamp as id)]
        (. Thread (sleep 10))
        (write-session as cookie {:foo 2})
        (is (not (= ts1 (read-timestamp as id))))
        (is (= (read-session as cookie) {:foo 2}))))))

(deftest timestamp-on-read
  (testing "Test the behaviour where each entry's timestamp is refreshed on read."
    (let [as (aging-memory-store :refresh-on-read true)
          cookie (write-session as nil {:a 1})
          id (get-id cookie)]
      (let [ts1 (read-timestamp as id)]
        (. Thread (sleep 10))
        (is (= (read-session as cookie) {:a 1}))
        (is (not (= ts1 (read-timestamp as id))))
        (is (= (read-session as cookie) {:a 1}))))))
