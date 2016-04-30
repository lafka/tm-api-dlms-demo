(ns api-dlms-demo.connection
  (:use org.httpkit.server)
  (:require [api-dlms-demo.store.connection :as connection]
            [api-dlms-demo.pubsub :as pubsub]
            [api-dlms-demo.debug :as debug]
            [api-dlms-demo.meter.dlms :as dlms]
            [api-dlms-demo.cloud.transport :as transport]
            [clojure.data.json :as json]
            [clojure.core.async :as async])
  (:import (org.openmuc.jdlms CloudConnectionBuilder AttributeAddress ObisCode AccessResultCode)
           (org.openmuc.jdlms.interfaceclass.attribute AssociationLnAttribute AttributeDirectory)
           (java.io EOFException IOException)
           (java.util.concurrent TimeoutException)
           (org.openmuc.jdlms.interfaceclass InterfaceClass)
           (api_dlms_demo.meter MethodAccessMode AttributeAccessMode)
           (org.openmuc.jdlms.interfaceclass.method MethodDirectory MethodDirectory$MethodNotFoundException)
           (org.openmuc.jdlms.interfaceclass.attribute AttributeDirectory AttributeDirectory$AttributeNotFoundException)))


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


; 0.0.96.1.0.255 - meter serial number
; 0.0.96.1.1.255 - manfucatorer name
; 1.0.0.2.0.255  - firmware revision
; 0.0.94.91.9.255- meter-type
; 1/0.0.96.1.0.255/1"
(defn handle-get-info [ref subscriber]
  (try
    (let [conn (connection/get ref)
          sn (new AttributeAddress 1 (new ObisCode "0.0.96.1.0.255") 2)
          mf (new AttributeAddress 1 (new ObisCode "0.0.96.1.1.255") 2)
          fw (new AttributeAddress 1 (new ObisCode "1.0.0.2.0.255") 2)
          results-sn (.get (.get conn (into-array AttributeAddress [sn])) 0)
          results-mf (.get (.get conn (into-array AttributeAddress [mf])) 0)
          results-fw (.get (.get conn (into-array AttributeAddress [fw])) 0)]
      
      (pubsub/publish ref {:ev     :results
                           :origin "client"
                           :call   "get-info"
                           :get-info {
                                       :serial-number (new String (.value (.resultData results-sn)))
                                       :firmware      (new String (.value (.resultData results-fw)))
                                       :manufactorer  (new String (.value (.resultData results-mf)))
                                       }}))
    (catch TimeoutException e (publish-error ref (str "electricity_id - timeout: " (.getMessage e))))))



(defn map-attr [attr class-id interface-class obis-code]
  (let [val (.value attr)
        id (.value (.get val 0))
        access-mode (AttributeAccessMode/accessModeFor (.intValue (.value (.get val 1))))
        selectors (.get val 2)
        attr-id-str (try
                      (let [class (AttributeDirectory/attributeClassFor interface-class (.intValue id))]
                        (format "%s%d" (.attributeName class) (.attributeId class)))
                    (catch AttributeDirectory$AttributeNotFoundException e (String/valueOf (.intValue id))))
        address (format "%d/%s/%d" class-id (.toDecimal obis-code) (.intValue id))
        selective-access (if (.isNull selectors)
                           []
                           (map #(.intValue (.value %)) (.value selectors)))
        ]

    {
     :attribute-addr   address
     :id               attr-id-str
     :access-mode      (.name access-mode)
     :selective-access selective-access
     }
    ))

(defn map-method [method class-id interface-class obis-code]
  (let [val (.value method)
        id (.value (.get val 0))
        access (.get val 1)
        access-mode (if (.isBoolean access)
                      (MethodAccessMode/accessModeFor (.value access))
                      (MethodAccessMode/accessModeFor (.intValue (.value access))))
        address (format "%d/%s/%d" class-id (.toDecimal obis-code) (.intValue id))
        method-id-str (try
                      (let [class (MethodDirectory/methodClassFor interface-class (.intValue id))]
                        (format "%s%d" (.methodName class) (.methodId class)))
                      (catch MethodDirectory$MethodNotFoundException e (String/valueOf (.intValue id))))
        ]

    {
     :method-addr address
     :id          method-id-str
     :access-mode (.name access-mode)
     :selective-access []
     }
    ))

(defn handle-get-attributes [ref subscriber]
  (try
  (let [conn (connection/get ref)
        attr (new AttributeAddress AssociationLnAttribute/OBJECT_LIST (new ObisCode "0.0.40.0.0.255"))
        results (.get (.get conn (into-array AttributeAddress [attr])) 0)
        code (.resultCode results)]

    (if (not=  AccessResultCode/SUCCESS)
      (throw (IOException. (str "Device sent error  code: " (.name code)))))

    (let [output (map
                    (fn [obj]
                      (let [items (.value obj)
                            class-id (.value (.get items 0))    ; int
                            version (.value (.get items 1))     ; number
                            ln (.value (.get items 2))          ; bytes
                            obis (new ObisCode ln)
                            access-rights (.value (.get items 3))
                            attrs (.value (.get access-rights 0))
                            methods  (.value (.get access-rights 1))
                            interface-class (InterfaceClass/interfaceClassFor class-id (.intValue version))
                            ]

                        {
                         :class-id (.name interface-class)
                         :medium (.name (.medium obis))
                         :obis (.toObisCode obis)
                         :attributes (map #(map-attr % class-id interface-class obis) attrs)
                         :methods (if (not= nil methods) (map #(map-method % class-id interface-class obis) methods) [])
                         })
                      )
                    (.value (.resultData results)))]





      (pubsub/publish ref {:ev     :results
                           :origin "client"
                           :call   "get-attributes"
                           :get-attributes output
                           }))


      )
  (catch TimeoutException e (publish-error ref (str "object_list - timeout: " (.getMessage e))))))

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
                  "get-info"           (handle-get-info ref subscriber)
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
