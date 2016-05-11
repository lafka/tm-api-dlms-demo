(defproject api-dlms-demo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :java-source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.7.0"]

                 ; for server
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring.middleware.logger "0.5.0"]
                 [http-kit "2.1.18"]

                 ; for API client
                 [http.async.client "0.5.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.codec "0.1.0"]

                 ; utilities
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/core.async "0.2.374"]

                 ;; DLMS implementation
                 [org.openmuc/jdlms "0.11.4"]
                 [org.openmuc/jasn1 "1.5.0"]
                 ]
  :main api-dlms-demo.cli
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
