(ns api-dlms-demo.conn.remote
  (:require [api-dlms-demo.util :as util]
            [api-dlms-demo.store.network :as network]
            [api-dlms-demo.store.device :as device]
            [api-dlms-demo.options :as options]
            [no.tinymesh.-api.http :as api-http]
            [api-dlms-demo.persistance.net-connectivity :as net-connectivity]
            [api-dlms-demo.store.network :as networks]))

(defn pred [ [{status :code} body _resp] ]
  (assert (= 200 status))
  body)

(defn get-network [nid]  (api-http/network nid (options/get)))
(defn get-networks []    (api-http/networks (options/get)))
(defn get-dev [resource] (api-http/device (namespace resource) (name resource)  (options/get)))
(defn get-devs [nid]     (api-http/devices nid (options/get)))

(defn refresh-all []
  (let [networks (util/backoff 1000 2 60000 get-networks pred)]
      (network/populate networks)
      (doall (pmap (fn [n]
              (println "refreshing " (n :key))
              (let [nid (keyword (get n :key))
                    devices (util/backoff 1000 2 60000 (fn [] (get-devs (get n :key))) pred)]

                (device/populate devices)
                (net-connectivity/aquire-locks nid [(assoc (networks/get nid) :devices (device/list nid))])
                ))
             networks))))

(defn refresh-items [items]
  (doseq [item items]
   (cond
     (nil? (namespace item)) (let [nid (name item)
                                   network (util/backoff 1000 2 60000 #(get-network nid) pred)
                                   devices (util/backoff 1000 2 60000 (fn [] (get-devs nid)) pred)]

                               (network/put (keyword nid) network)
                               (device/populate devices)
                               (net-connectivity/aquire-locks nid [(assoc network :devices devices)]))
     :else (let [device (util/backoff 1000 2 60000 #(get-dev item) pred)]
             (device/put item device)))))


; takes a list `resources` of ["nid[/device]", ...] items to refresh
; or `nil` to do reload all of it
(defn refresh [{resources :resources} _state _resp]
  (println "remote/refresh: reloading " resources)
  (case resources
    nil (refresh-all)
    (refresh-items (map #(keyword %) resources))
    ))