(defproject aging-session "0.3.1"
  :description "Memory based ring session with expiry and time based mutation."
  :url "https://github.com/diligenceengine/aging-session"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[ring/ring-core "1.2.2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}})