(ns aging-session.event)

(defn expires-after
  "Expires an entry if left untouched for a given number of seconds."
	[seconds]
  (let [ms (* 1000 seconds)]
	  (fn [now entry] (if-not (> (- now (:timestamp entry)) ms) entry))))
