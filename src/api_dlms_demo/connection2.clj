(ns api-dlms-demo.connection2
  (:use org.httpkit.server)
  (:require [clojure.data.json :as json]
            [clojure.core.async :as async]
            [api-dlms-demo.conn.state :as state]
            [api-dlms-demo.conn.store :as store]
            [api-dlms-demo.conn.remote :as remote]
            [api-dlms-demo.conn.dlms :as dlms]
            [api-dlms-demo.util :as util]
            [api-dlms-demo.store.pubsub :as pubsub]))

(defn parse [buf]
  (json/read-str buf :key-fn #(keyword %)))

(defn res-to-str [resource]
  (cond
    (nil? (namespace resource)) (name resource)
    :else (str (namespace resource) "/" (name resource))))

; every REQUEST takes the form: {"request": <req>, <a1>: <v1>}
(defn handler [req]
  (with-channel
    req
    websocket
    (let [ref (util/random-string 16)
          state (state/init)
          chan (state/get state :chan)
          subscriber (pubsub/subscribe chan)
          resp (fn [data]
                 (try
                  (send! websocket (json/write-str (merge data {:ref ref})))
                  (catch Throwable e
                    (println "connection2: failed to write JSON:")
                    (.printStackTrace e)
                    )))]

      (on-close websocket (fn [_status] (do
                                         (println "conn:" ref "closed")
                                         )))
      (on-receive
        websocket
        (fn [data]
          (try
            (let [json (parse data)]

            (case (get json :request)
               "ping" (resp {:pong true})

               "dlms-queue" (dlms/handle json state resp)
               "dlms-queue-state" (dlms/get-queue json state resp)

               "dlms-attributes" (dlms/get-attributes json state resp)

               ;"dlms-attrs-cached" nil
               ;"dlms-attrs-cache" nil
               ;"dlms-object-list" nil

               "remote-refresh" (remote/refresh json state resp)

               "store-networks" (store/networks json state resp)
               "store-network" (store/network json state resp)
               "store-device" (store/device json state resp)
               "store-devices" (store/devices json state resp)

               ;"storage-subscribe"   (storage/subscribe json state resp)
               ;"storage-unsubscribe" (storage/unsubscribe json state resp)
               (resp {:error "invalid request" :request (get json :request)})))
            (catch Throwable t
              (if (.startsWith (or (.getMessage t) "") "JSON error")
                (resp {:error "failed to parse json" :message (.getMessage t)})
                (throw t))))))

      (async/go-loop []
        (let [{chan-name :chan type :type resource :resource data :data} (async/<! subscriber)]
          (resp {:event :pubsub
                 :channel chan-name
                 :type type
                 :resource (res-to-str (keyword resource))
                 :data data})
          (recur)))
      )))