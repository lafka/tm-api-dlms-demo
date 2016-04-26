(ns api-dlms-demo.handler
  (:use ring.util.response)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.data.json :as json]
            [http.async.client :as http]
            [api-dlms-demo.store.network :as networks]
            [api-dlms-demo.store.device :as devices]
            [compojure.response :as response]))

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


(defn post-action [nid device])

(defn get-stream [nid device])

(defn index [] (rfn request (-> (resource-response "index.html" {:root "public"})
                                (content-type "text/html"))))


(defroutes app-routes
  (GET "/api/network" [] (get-networks))
  (GET "/api/network/:nid" [nid] (get-network nid))
  (POST "/api/network/:nid" [nid] (post-network nid))
  (POST "/api/action/:nid/:device" [nid device] (post-action nid device))
  (GET "/api/stream/:nid/:device" [nid device] (get-stream nid device))
  (route/resources "/")
  (index))

(def config
  {:params    {:urlencoded true
               :multipart  true
               :nested     true
               :keywordize true}
   :cookies   false
   :session   {:flash true
               :cookie-attrs {:http-only true}}
   :security  false
   :static    {:resources "public"}
   :responses {:not-modified-responses true
               :absolute-redirects     true
               :content-types          true
               :default-charset        "utf-8"}})


(defn wrap-dir-index [handler]
  (fn [req]
    (handler
      (update-in req [:uri] #(if (= "/" %) "/index.html" %)))
    ))

(def app
  (-> (wrap-defaults app-routes config)
      (wrap-dir-index)))