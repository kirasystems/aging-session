(defproject kirasystems/aging-session "0.3.2"
  :description "Memory based ring session with expiry and time based mutation."
  :url "https://github.com/diligenceengine/aging-session"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories [["releases" {:url "https://clojars.org/repo"
                              :sign-releases false
                              :username :env
                              :password :env}]]

  :dependencies [[ring/ring-core "1.4.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
