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
            [http.async.client :as http]
            [api-dlms-demo.store.network :as networks]
            [api-dlms-demo.store.device :as devices]
            [api-dlms-demo.connection :as connection]
            [ring.util.response :as response]))

(def options {:remote "http://http.stage.highlands.tiny-mesh.com/v2"
               :user "dev@nyx.co"
               :password "1qaz!QAZ"})

(defn http-get [path options]
  (let [remote (get options :remote)
        user (get options :user)
        password (get options :password)
        endpoint (str remote path)]

    (with-open [client (http/create-client )]
      (println "GET " endpoint)
      (let [resp (http/GET client endpoint :auth {:type :basic :user user :password password :preemptive true})]
        (json/read-str
          (-> resp
            http/await
            http/string)
          :key-fn #(keyword %))))))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defn not-found-response []
  (json-response {:error "not found"} 404))

(defn project-net [nid]
  (let [network (networks/get nid)]
    (if (= nil network)
      nil
      (assoc network :devices (devices/list nid))
      )))

(defn post-network [nid]
  (let [network (http-get (str "/network/" nid) options)
        devices (http-get (str "/device/" nid) options)]

    (networks/put nid network)
    (devices/populate devices)

    (json-response (project-net nid) 205)))

(defn get-networks []
  (let [networks (http-get (str "/network") options)]

    ;; populate devices in parallel
    (doall (pmap #(devices/populate (http-get (str "/device/" (get % :key)) options))
                 networks))

    (networks/populate networks)

    (json-response (map #(project-net (get % :key)) networks) 200)))


(defn get-network [nid]
  (let [network (project-net nid)]
    (cond
      (= nil network) (not-found-response)
      :else (json-response network 200)
      )))


(defroutes app-routes
  (GET  "/api/network" [] (get-networks))
  (GET  "/api/network/:nid" [nid] (get-network nid))
  (POST "/api/network/:nid" [nid] (post-network nid))
  (GET "/api/connection/:nid/:device" [] connection/handler)
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
  (-> (site #'app-routes)
      (logger/wrap-with-logger)
      (reload/wrap-reload)
      (run-server {:port port})))