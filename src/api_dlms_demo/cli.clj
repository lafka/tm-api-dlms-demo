(ns api-dlms-demo.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [no.tinymesh.-api.config :as api-config]
            [api-dlms-demo.handler :as server]
            [api-dlms-demo.options :as options])
  (:gen-class))

(def cli-options
  [["-r" "--remote" "API endpoint"
    :id :remote
    :required "REMOTE"
    :default (options/get :remote)]
   ["-u" "--user" "API user"
    :id :user
    :required "USER"
    :default (options/get :user)]
   ["-p" "--password" "API password"
    :id :password
    :required "PASSWORD"
    :default (options/get :password)]
   ["-v" nil "Verbosity level; may be specified multiple times to increase value"

    :id :verbosity
    :default (options/get :verbosity)
    ;; Use assoc-fn to create non-idempotent options
    :assoc-fn (fn [m k _] (update-in m [k] inc))]

   ["-P" "--port" "local listening port"
    :id :port
    :required "PORT"
    :default (options/get :port)]

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
  (let [{:keys [options _arguments errors summary]} (clojure.tools.cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))

    ;(org.apache.log4j.BasicConfigurator/configure)

    ; update options
    (options/update options)
    (api-config/update (select-keys options (keys (api-config/get))))


    (onelog.core/info "starting server")
    (server/start (read-string (options :port)))
    (onelog.core/info "server started")
    ))