(ns aging-session.event_test
  (:require [aging-session.event :as event])
	(:use clojure.test
	      ring.middleware.session.store
	      aging-session.memory))

(deftest session-expiry
	(testing "Test session expiry."
		(let [as (aging-memory-store :events [(event/expires-after 1)])]
			(write-session as "mykey" {:foo 1})
			(. Thread (sleep 1500))
			(is (= (read-session as "mykey") {})))))

(deftest session-expiry-by-sweep
	(testing "Test session expiry sweep."
		(let [as (aging-memory-store
						   :events      [(event/expires-after 1)]
							 :sweep-every 5
							 :sweep-delay 1000)]
			(write-session as "mykey" {:foo 1})
			(. Thread (sleep 1500))
      ; key should still exist, even though it's expired
      (is (not (nil? (read-timestamp as "mykey"))))

      ; key should exist for three more writes
			(write-session as "other-key" {:foo 1})
			(is (not (nil? (read-timestamp as "mykey"))))
			(write-session as "other-key" {:foo 1})
			(is (not (nil? (read-timestamp as "mykey"))))
			(write-session as "other-key" {:foo 1})
			(is (not (nil? (read-timestamp as "mykey"))))

      ; on the fifth write and after 30s, key should not exist
			(write-session as "other-key" {:foo 1})
			(. Thread (sleep 2000))
      (is (nil? (read-timestamp as "mykey"))))))