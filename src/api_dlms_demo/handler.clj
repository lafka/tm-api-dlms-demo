(ns api-dlms-demo.handler
  (:use [org.httpkit.server :only [run-server]])
  (:require [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.reload :as reload]
            [ring.middleware.logger :as logger]
            [ring.util.response :refer [content-type status file-response resource-response]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            [clojure.data.json :as json]
            [api-dlms-demo.persistance.net-connectivity :as net-connectivity]
            [api-dlms-demo.connection2 :as connection2]
            [api-dlms-demo.options :as options]
            [api-dlms-demo.store.device :as device]
            [no.tinymesh.-api.http :as api-http]
            [api-dlms-demo.store.network :as network]
            [api-dlms-demo.util :as util]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defn not-found-response []
  (json-response {:error "not found"} 404))

(defroutes app-routes
  (GET "/ws" [] connection2/handler)
  (GET "/favicon.ico"  [] {:status (or status 404)
                           :headers {"Content-Type" "text/plain"}
                           :body ""})
  (route/resources "/")
  (rfn request
     (-> (resource-response "index.html" {:root "public"})
         (content-type "text/html")
         (status 200)))
  (route/not-found "nope"))


(defn start [port]
  (let [
        pred (fn [ [{status :code} body _resp] ]
               (assert (= 200 status))
               body)

        fun  #(api-http/networks (options/get))
        get-devs #(api-http/devices (get % :key) (options/get))]

    (util/forever #(let [networks (util/backoff 1000 2 60000 fun pred)]
                    (network/populate networks)
                    (doall (pmap (fn [n] (device/populate
                                           (util/backoff 1000 2 60000 (fn [] (get-devs n)) pred)))
                                 networks))
                    (net-connectivity/ensure-network-streams))))

  (-> (site #'app-routes)
      (logger/wrap-with-logger)
      (reload/wrap-reload)
      (run-server {:port port})))