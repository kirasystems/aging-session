# aging-session

A memory based ring session store that has a concept of time. The primary goal
is to allow the session store to deallocate old sessions. While much of this
may be written on top of the standard ring session store, there is ultimately
no way to get rid of sessions that are no longer being visited. 

Depending on how long running a server is and on how big its sessions are, 
the unallocated sessions can potentially accumulate more and more memory.
Another possible scenario is a denial of service attack where the attacker
continually asks for new sessions thus exhusting the server of memory.

This session store has a sweeper thread that will apply a set of functions
to every session object after every X requests are made. These functions
are also applied to every session when it is read.

## Usage

The following creates a memory aging store that refreshes the timestamp every
time the session is written and erases entries after 1 hour.

```clojure
(ns myapp
  (:use 
    ring.middleware.session
    aging-session.memory)
  (:require ['aging-session.event :as event]))

(def app
  (wrap-session handler {:store (aging-memory-store 
	                                :refresh-on-write true
								                  :events           [(event/expires-after 3600)])}))
```

Event functions take two parameters: the current timestamp and a session entry
with a timestamp key and an value key. The timestamp key stores the sessions
timestamp and the value key stores the session itself. Functions should return
a new entry, or nil. If they return nil, the session entry is deleted. The
expires after function illustrates this.

```clojure
(defn expires-after
  "Expires an entry if left untouched for a given number of seconds."
	[seconds]
  (let [ms (* 1000 seconds)]
	  (fn [now entry] (if-not (> (- now (:timestamp entry)) ms) entry))))
```

Event functions are applied in order and can be used to modify sessions in
any time-based way. For instance, one may wish to set a reauthentication flag
in sessions older than 1 hour, and delete sessions older than 2 hours. 


## License

Copyright © 2012 DiligenceEngine Inc.

Distributed under the Eclipse Public License, the same as Clojure.
