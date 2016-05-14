(ns api-dlms-demo.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [api-dlms-demo.handler :as server]
            [api-dlms-demo.persistance.worker :as worker])
  (:gen-class))

(def cli-options
  [["-r" "--remote" "API endpoint"
    :id :remote
    :required "REMOTE"
    :default "http://http.stage.highlands.tiny-mesh.com/v2"]
   ["-u" "--user" "API user"
    :id :user
    :required "USER"
    :default "dev@nyx.co"]
   ["-p" "--password" "API password"
    :id :password
    :required "PASSWORD"
    :default "1qaz!QAZ"]
   ["-v" nil "Verbosity level; may be specified multiple times to increase value"

    :id :verbosity
    :default 0
    ;; Use assoc-fn to create non-idempotent options
    :assoc-fn (fn [m k _] (update-in m [k] inc))]

   ["-P" "--port" "local listening port"
    :id :port
    :required "PORT"
    :default "3001"]

   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["communicate with DLMS meters in a Tinymesh Cloud Network"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        " scan"]
       (string/join \newline)))

(defn error-msg [errors]
  (str "An error occured:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))





(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (clojure.tools.cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))

    ;(org.apache.log4j.BasicConfigurator/configure)

    (reset! api-dlms-demo.cloud.transport/cliopts options)


    (worker/init)


    ;; ingress stream for events
    ;(async/go-loop []
    ;  (let [t (transport/-transport-factory (keyword "2/3CJsndr9J2gkI") nil)]
    ;    (.startListening t nil)
    ;    (recur)))


    (onelog.core/info "starting server")
    (server/start (read-string (options :port)))
    (onelog.core/info "server started")
    ))