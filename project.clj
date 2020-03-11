(defproject kirasystems/aging-session "0.5.1-SNAPSHOT"
  :description "Memory based ring session with expiry and time based mutation."
  :url "https://github.com/diligenceengine/aging-session"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories [["releases" {:url "https://clojars.org/repo"
                              :sign-releases false
                              :username :env/clojars_username
                              :password :env/clojars_password}]]

  :dependencies [[ring/ring-core "1.7.1"]
                 [buddy/buddy-auth "2.2.0"]
                 [buddy/buddy-core "1.6.0"]
                 [buddy/buddy-sign "3.1.0"]
                 [buddy/buddy-hashers "1.4.0"]
                 [clj-time "0.15.2"]
                 [commons-codec "1.13"]
                 [com.taoensso/nippy "2.14.0"]
                 [org.clojure/tools.logging "0.5.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.9.0"]]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
