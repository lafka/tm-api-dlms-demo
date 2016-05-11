(ns api-dlms-demo.connection
  (:use org.httpkit.server)
  (:require [api-dlms-demo.store.connection :as connection]
            [api-dlms-demo.pubsub :as pubsub]
            [api-dlms-demo.persistance.worker :as worker]
            [api-dlms-demo.persistance.cache :as cache]
            [api-dlms-demo.meter.attribute-list :as attribute-list]
            [clojure.data.json :as json]
            [clojure.core.async :as async]
            ))


(defn parse [buf]
  (json/read-str buf :key-fn #(keyword %)))

(defn publish-error [ref message]
  (pubsub/publish ref {:ev     :error
                       :origin "client"
                       :message message}))


(defn handler [req]
  (let [{{nid :nid dev :device} :params} req
        ref (keyword nid dev)]

    (with-channel
      req
      websocket
      (let [subscriber (async/chan)]
        (on-close websocket (fn [status] (do
                                           (pubsub/unsubscribe ref subscriber)
                                           (async/close! subscriber)
                                           (let [conn (connection/get ref)]
                                             (if (not= nil conn)
                                               (do
                                                (.disconnect (connection/get ref) false)
                                                (connection/delete ref))))

                                           (onelog.core/info (str "channel[" ref "] closed: " status)))))

        (async/go-loop []
                 (let [data (async/<!! subscriber)]
                   (if (not= nil data) ; data := nil when channel is closed
                     (do
                       (send! websocket (json/write-str data))
                       (recur)))))



        (on-receive
          websocket
          (fn [data]
            ;(try
              (let [json (parse data)
                    handle-resp (fn [resp] (send! websocket (json/write-str (merge resp {:ref ref}))))]

                (case (get json :ev)
                  "read-worker"        (worker/handle ref (get json :attrs) )
                  "write-worker"       (worker/write ref (get json :attrs) )

                  "read"               (cache/read  ref [(get json :iface) (get json :obis) (get json :attr)] handle-resp)
                  "list-attrs"         (cache/list  ref)
                  "object-list"        (attribute-list/handle ref)

                  (send! websocket (json/write-str {:error "invalid-command"}))))
              ;(catch Exception e (do
               ;                    (onelog.core/error (str "caught exception: " (.getMessage e)))
                ;                   (send! websocket (json/write-str {:error "exception" :msg (.getMessage e)})))
            ;))
            ))

        (pubsub/subscribe ref subscriber)
        (onelog.core/info (str "channel[" ref "] opened"))
        ))))