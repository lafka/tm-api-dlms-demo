(ns no.tinymesh.-api.http
  (:require [http.async.client :as http]
            [no.tinymesh.-api.config :as config]
            [clojure.data.json :as json])
  (:use http.async.client.request))

(defn req [method path options]
  (let [remote (get options :remote)
        endpoint (str remote path)
        opts {:auth (config/auth options) :query (get options :query {})}]

    (println "api-req-http:" method endpoint)

    (with-open [client (http/create-client)]

      (let [resp (execute-request client (apply prepare-request method endpoint (apply concat opts)))
            body (json/read-str
                   (-> resp
                       http/await
                       http/string)
                   :key-fn #(keyword %))
            status (http/status resp)]

        [status body resp]
        ))))

(defn network  [nid options] (req :get (str "/network/" nid) options))
(defn networks [options] (req :get "/network" options))

(defn device  [nid device options] (req :get (str "/device/" nid "/" device) options))
(defn devices [nid options] (req :get (str "/device/" nid) options))