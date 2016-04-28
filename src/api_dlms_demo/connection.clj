(ns api-dlms-demo.connection
  (:use org.httpkit.server)
  (:require [api-dlms-demo.store.connection :as connection]
            [api-dlms-demo.pubsub :as pubsub]
            [api-dlms-demo.meter.dlms :as dlms]
            [api-dlms-demo.cloud.transport :as transport]
            [clojure.data.json :as json]
            [clojure.core.async :as async])
  (:import (org.openmuc.jdlms CloudConnectionBuilder AttributeAddress ObisCode LnClientConnection AccessResultCode)
           (org.openmuc.jdlms.interfaceclass.attribute AssociationLnAttribute)
           (java.io EOFException IOException)
           (java.util.concurrent TimeoutException)))


(defn parse [buf]
  (json/read-str buf :key-fn #(keyword %)))

(defn publish-error [ref message]
  (pubsub/publish ref {:ev     :error
                       :origin "client"
                       :message message}))

(defn publish-not-connected [ref] (publish-error ref "not connected"))

(defn handle-connect [ref subscriber]
  (let [transport (transport/-transport-factory ref subscriber)
        settings dlms/settings
        builder (new CloudConnectionBuilder settings transport)]

    (println (str "connection[" ref "] transport connection buildt"))

    (try
      (do
        (CloudConnectionBuilder/connect (connection/put ref (.buildLnConnection builder)))
        (pubsub/publish ref {:ev      :connected
                             :origin  "client"
                             :message "connected"})
        )
      (catch IOException e (do
        (pubsub/publish ref {:ev      :error
                            :origin  "client"
                            :message (.getMessage e)})
                             ))
      (catch EOFException e (do
        (pubsub/publish ref {:ev      :error
                             :origin  "client"
                             :message (.getMessage e)})
        (.close transport)
        (connection/delete ref))))
    ))

(defn handle-disconnect [ref subscriber]
  (let [conn (connection/get ref)]
    (case conn
      nil (publish-not-connected ref)
      (.disconnect conn))))

(defn handle-get-serial-number [ref subscriber]
  (try
    (let [conn (connection/get ref)
          attr (new AttributeAddress AssociationLnAttribute/LOGICAL_NAME (new ObisCode "1.0.0.0.255.255"))
          results (.get (.get conn (into-array AttributeAddress [attr])) 0)]

    (clojure.pprint/pprint results))
   (catch TimeoutException e (publish-error ref (str "timedout waiting for electricity_id: " (.getMessage e))))))

(defn handle-get-attributes [ref subscriber]
  (try
  (let [conn (connection/get ref)
        attr (new AttributeAddress AssociationLnAttribute/OBJECT_LIST (new ObisCode "0.0.40.0.0.255"))
        results (.get (.get conn (into-array AttributeAddress [attr])) 0)]

    (clojure.pprint/pprint results)

      )
  (catch TimeoutException e (publish-error ref (str "timed out waiting for object_list response" (.getMessage e))))))

(defn handle-get-eventlog [ref subscriber] nil)


;; - `websocket` connection is initiated
;; - create a pubsub topic `ref`, `subscriber` consumes the topic
;; - `subscriber` pushes all data recevied to `websocket`
;; - call `handle-connect` with `subscriber
;;  - `handle-connect` builds `builder :: CloudConnectionBuilde` connection with `subscriber`
;;  - (.buildLnConnection builder)` calls `cloud.transport/startListening`
;;    - `cloud.transport` uses a different `async/chan` pipeline to communicate between cloud stream and main proc.
;;    - `cloud.transport/startListening` opens up a message stream to cloud API
;;    - `cloud.transport/startListening` calls (hdlc/connect) which uses `transport/sendraw` to send raw HDLC frames
;;     - `hdlc/connect` uses `state.ref` to call `(pubsub/publish ref <data>)`


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
              (let [json (parse data)]
                (onelog.core/info (str "ev: " + data))
                (case (get json :ev)
                  "connect"            (handle-connect ref subscriber)
                  "disconnect"         (handle-disconnect ref subscriber)
                  "get-serial-number"  (handle-get-serial-number ref subscriber)
                  "get-attributes"     (handle-get-attributes ref subscriber)
                  "get-eventlog"       (handle-get-eventlog ref subscriber)
                  (send! websocket (json/write-str {:error "invalid-command"}))))
              ;(catch Exception e (do
               ;                    (onelog.core/error (str "caught exception: " (.getMessage e)))
                ;                   (send! websocket (json/write-str {:error "exception" :msg (.getMessage e)})))
            ;))
            ))

        (pubsub/subscribe ref subscriber)
        (onelog.core/info (str "channel[" ref "] opened"))
        ))))
