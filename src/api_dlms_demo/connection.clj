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
        (CloudConnectionBuilder/connect (connection/put ref (.buildLnConnection2 builder)))
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

(defn read-obis [conn class obis attr]
  (try
    (print "reading obis: " class "/" obis "/" attr "\t->\t")
    (let [res (.resultData (.get (.get conn (into-array AttributeAddress [(new AttributeAddress class  (new ObisCode obis) attr)   ])) 0))]
      (if (not= nil res)
        (.toString (.value res))
        nil))
    (catch Exception e (.toString e))))


(defn read-obis2 [conn class obis attr fmt]
  (try
    (print "reading obis: " class "/" obis "/" attr "\t->\t")
    (let [res (.resultData (.get (.get conn (into-array AttributeAddress [(new AttributeAddress class  (new ObisCode obis) attr)   ])) 0))]
      (if (not= nil res)
        (fmt res)
        nil))
  (catch Exception e (.toString e))))

(defn handle-read [ref subscriber]
  (try
    (let [conn (connection/get ref)
      logical-device-name          (read-obis2 conn 1  "0.0.42.0.0.255"   2 (fn [res] (new String (.value res))))
      meter-serial-number          (read-obis2 conn 1  "0.0.96.1.0.255"   2 (fn [res] (new String (.value res))))
      manufacturers-name           (read-obis2 conn 1  "0.0.96.1.1.255"   2 (fn [res] (new String (.value res))))
      firmware-version             (read-obis2 conn 1  "1.0.0.2.0.255"    2 (fn [res] (new String (.value res))))
      meter-type                   (read-obis conn 1  "0.0.94.91.9.255"   2)
      category                     (read-obis2 conn 1  "0.0.94.91.11.255" 2 (fn [res] (new String (.value res))))
      current-rating               (read-obis2 conn 1  "0.0.94.91.12.255" 2( fn [res] (new String (.value res))))
      manufactur-year              (read-obis conn 1  "0.0.96.1.4.255"    2)
      demand-integration-period    (read-obis conn 1  "1.0.0.8.0.255"     2)
      block-load-integration-period(read-obis conn 1  "1.0.0.8.4.255"     2)
      daily-load-capture-period    (read-obis conn 1  "1.0.0.8.5.255"     2)
      cumulative-tamper-count      (read-obis conn 1  "0.0.94.91.0.255"   2)
      cumulative-billing-count     (read-obis conn 1  "0.0.0.1.0.255"     2)
      programming-count            (read-obis conn 1  "0.0.96.2.0.255"    2)
      event0-code-object           (read-obis conn 1  "0.0.96.11.0.255"   2)
      event1-code-object           (read-obis conn 1  "0.0.96.11.1.255"   2)
      event2-code-object           (read-obis conn 1  "0.0.96.11.2.255"   2)
      event3-code-object           (read-obis conn 1  "0.0.96.11.3.255"   2)
      event4-code-object           (read-obis conn 1  "0.0.96.11.4.255"   2)
      available-billing-cycles     (read-obis conn 1  "0.0.0.1.1.255"     2)
      over-current-for-cut-off     (read-obis conn 1  "0.128.128.0.0.255" 2)
      over-load-for-cut-off        (read-obis conn 1  "0.128.128.0.1.255" 2)
      connection-period-interval   (read-obis conn 1  "0.128.128.0.2.255" 2)
      connection-lockout-time      (read-obis conn 1  "0.128.128.0.3.255" 2)
      connection-time-repeat       (read-obis conn 1  "0.128.128.0.4.255" 2)
      tamper-occurance-time        (read-obis conn 1  "0.128.128.0.5.255" 2)
      tamper-restoration-time      (read-obis conn 1  "0.128.128.0.6.255" 2)

      voltage                      (read-obis conn 3  "1.0.12.7.0.255"    2) ; blocks
      phase-current                (read-obis conn 3  "1.0.11.7.0.255"    2) ; float??
      neutral-current              (read-obis conn 3  "1.0.91.7.0.255"    2) ; float??

      signed-power-factor          (read-obis conn 3  "1.0.13.7.0.255"    2) ; blocks
      frequency                    (read-obis conn 3  "1.0.14.7.0.255"    2) ; blocks
      apparent-power               (read-obis conn 3  "1.0.9.7.0.255"     2) ; float
      active-power                 (read-obis conn 3  "1.0.1.7.0.255"     2) ; float

      cumulative-active-energy     (read-obis conn 3  "1.0.1.8.0.255"     2) ; blocks
      cumulative-apparent-energy   (read-obis conn 3  "1.0.9.8.0.255"     2) ; blocks
      cumulative-power-on          (read-obis conn 3  "0.0.94.91.14.255"  2) ; blocks
      average-voltage              (read-obis conn 3  "1.0.12.27.0.255"   2) ; blocks
      block-kwh                    (read-obis conn 3  "1.0.1.29.0.255"    2) ; float
      bloack-kvah                  (read-obis conn 3  "1.0.9.29.0.255"    2) ; float
      billing-date                 (read-obis2 conn 3  "0.0.0.1.2.255"    2 debug/dt-to-str2)
      bp-average-power-factor      (read-obis conn 3  "1.0.13.0.0.255"    2) ; float
      tz1-kwh                      (read-obis conn 3  "1.0.1.8.1.255"     2) ; bytebuffer
      tz2-kwh                      (read-obis conn 3  "1.0.1.8.2.255"     2) ; blocks
      tz3-kwh                      (read-obis conn 3  "1.0.1.8.3.255"     2) ; float
      tz4-kwh                      (read-obis conn 3  "1.0.1.8.4.255"     2) ; float
      tz1-kvah                     (read-obis conn 3  "1.0.9.8.1.255"     2) ; float
      tz2-kvah                     (read-obis conn 3  "1.0.9.8.2.255"     2) ; blocks
      tz3-kvah                     (read-obis conn 3  "1.0.9.8.3.255"     2) ; float
      tz4-kvah                     (read-obis conn 3  "1.0.9.8.4.255"     2) ; bytebuffer
      active-current               (read-obis conn 3  "1.0.94.91.14.255"  2) ; bytebufer
      total-power-on-time          (read-obis conn 3  "0.0.94.91.13.255"  2) ; blocks
      kw-md-with-date-and-time     (read-obis conn 4  "1.0.1.6.0.255"     2)
      kva-md-with-date-and-time    (read-obis conn 4  "1.0.9.6.0.255"     2)
      instantaneous-profile        (read-obis conn 7  "1.0.94.91.0.255"   2) ; blocks
      instantaneous-scaler-profile (read-obis conn 7  "1.0.94.91.3.255"   2)
      block-load-profile           (read-obis conn 7  "1.0.99.1.0.255"    2) ; fragmentation
      block-load-scaler-profile    (read-obis conn 7  "1.0.94.91.4.255"   2)
      daily-load-profile           (read-obis conn 7  "1.0.99.2.0.255"    2) ; fragmentation
      daily-load-scaler-profile    (read-obis conn 7  "1.0.94.91.5.255"   2)
      billing-profile              (read-obis conn 7  "1.0.98.1.0.255"    2) ; fragmentation
      billing-scaler-profile       (read-obis conn 7  "1.0.94.91.6.255"   2)
      event0-scaler-profile        (read-obis conn 7  "1.0.94.91.7.255"   2)
      event0-profile               (read-obis conn 7  "0.0.99.98.0.255"   2) ; fragmentation
      event1-profile               (read-obis conn 7  "0.0.99.98.1.255"   2) ; fragmentation
      event2-profile               (read-obis conn 7  "0.0.99.98.2.255"   2)
      event3-profile               (read-obis conn 7  "0.0.99.98.3.255"   2) ; fragmentation
      event4-profile               (read-obis conn 7  "0.0.99.98.4.255"   2) ; bytebuffer
      name-plate-detail            (read-obis conn 7  "0.0.94.91.10.255"  2)
      real-time-clock              (read-obis2 conn 8  "0.0.1.0.0.255"    2 debug/dt-to-str2)
      association0                 (read-obis conn 15 "0.0.40.0.1.255"    2) ; bytebuffer
      association1                 (read-obis conn 15 "0.0.40.0.2.255"    2) ; fragmentation
      association2                 (read-obis conn 15 "0.0.40.0.3.255"    2)
      activity-calender            (read-obis conn 20 "0.0.13.0.0.255"    2)
      single-action-schedule       (read-obis conn 22 "0.0.15.0.0.255"    2)
      disconnect-control           (read-obis conn 70 "0.0.96.3.10.255"   2)


 ]


        (pubsub/publish ref {:ev     :results
                             :origin "client"
                             :call   "get-data"
                             :get-data {
                                        :logical-device-name                logical-device-name
                                        :meter-serial-number                meter-serial-number
                                        :manufacturers-name                 manufacturers-name
                                        :firmware-version                   firmware-version
                                        :meter-type                         meter-type
                                        :category                           category
                                        :current-rating                     current-rating
                                        :manufactur-year                    manufactur-year
                                        :demand-integration-period          demand-integration-period
                                        :block-load-integration-period      block-load-integration-period
                                        :daily-load-capture-period          daily-load-capture-period
                                        :cumulative-tamper-count            cumulative-tamper-count
                                        :cumulative-billing-count           cumulative-billing-count
                                        :programming-count                  programming-count
                                        :event0-code-object                 event0-code-object
                                        :event1-code-object                 event1-code-object
                                        :event2-code-object                 event2-code-object
                                        :event3-code-object                 event3-code-object
                                        :event4-code-object                 event4-code-object
                                        :available-billing-cycles           available-billing-cycles
                                        :over-current-for-cut-off           over-current-for-cut-off
                                        :over-load-for-cut-off              over-load-for-cut-off
                                        :connection-period-interval         connection-period-interval
                                        :connection-lockout-time            connection-lockout-time
                                        :connection-time-repeat             connection-time-repeat
                                        :tamper-occurance-time              tamper-occurance-time
                                        :tamper-restoration-time            tamper-restoration-time
                                        :voltage                            voltage
                                        :phase-current                      phase-current
                                        :neutral-current                    neutral-current
                                        :signed-power-factor                signed-power-factor
                                        :frequency                          frequency
                                        :apparent-power                     apparent-power
                                        :active-power                       active-power
                                        :cumulative-active-energy           cumulative-active-energy
                                        :cumulative-apparent-energy         cumulative-apparent-energy
                                        :cumulative-power-on                cumulative-power-on
                                        :average-voltage                    average-voltage
                                        :block-kwh                          block-kwh
                                        :bloack-kvah                        bloack-kvah
                                        :billing-date                       billing-date
                                        :bp-average-power-factor            bp-average-power-factor
                                        :tz1-kwh                            tz1-kwh
                                        :tz2-kwh                            tz2-kwh
                                        :tz3-kwh                            tz3-kwh
                                        :tz4-kwh                            tz4-kwh
                                        :tz1-kvah                           tz1-kvah
                                        :tz2-kvah                           tz2-kvah
                                        :tz3-kvah                           tz3-kvah
                                        :tz4-kvah                           tz4-kvah
                                        :active-current                     active-current
                                        :total-power-on-time                total-power-on-time
                                        :kw-md-with-date-and-time           kw-md-with-date-and-time
                                        :kva-md-with-date-and-time          kva-md-with-date-and-time
                                        :instantaneous-profile              instantaneous-profile
                                        :instantaneous-scaler-profile       instantaneous-scaler-profile
                                        :block-load-profile                 block-load-profile
                                        :block-load-scaler-profile          block-load-scaler-profile
                                        :daily-load-profile                 daily-load-profile
                                        :daily-load-scaler-profile          daily-load-scaler-profile
                                        :billing-profile                    billing-profile
                                        :billing-scaler-profile             billing-scaler-profile
                                        :event0-scaler-profile              event0-scaler-profile
                                        :event0-profile                     event0-profile
                                        :event1-profile                     event1-profile
                                        :event2-profile                     event2-profile
                                        :event3-profile                     event3-profile
                                        :event4-profile                     event4-profile
                                        :name-plate-detail                  name-plate-detail
                                        :real-time-clock                    real-time-clock
                                        :association0                       association0
                                        :association1                       association1
                                        :association2                       association2
                                        :activity-calender                  activity-calender
                                        :single-action-schedule             single-action-schedule
                                        :disconnect-control                 disconnect-control
                                        }}))
    ;(let [conn (connection/get ref)
    ;      voltage             (new AttributeAddress 3 (new ObisCode "1.0.12.7.0.255") 3)
    ;      phase-current       (new AttributeAddress 3 (new ObisCode "1.0.11.7.0.255") 3)
    ;      neutral-current     (new AttributeAddress 3 (new ObisCode "1.0.91.7.0.255") 3)
    ;      signed-power-factor (new AttributeAddress 3 (new ObisCode "1.0.13.7.0.255") 3)
    ;      frequency           (new AttributeAddress 3 (new ObisCode "1.0.14.7.0.255") 3)
    ;      apparent-power      (new AttributeAddress 3 (new ObisCode "1.0.9.7.0.255") 3)
    ;      active-power        (new AttributeAddress 3 (new ObisCode "1.0.1.7.0.255") 3)
    ;      acc-active-energy   (new AttributeAddress 3 (new ObisCode "1.0.1.8.0.255") 3)
    ;      acc-apparent-energy (new AttributeAddress 3 (new ObisCode "1.0.9.8.0.255") 3)
    ;      acc-power-on        (new AttributeAddress 3 (new ObisCode "0.0.94.91.14.255") 3)
    ;      avg-voltage         (new AttributeAddress 3 (new ObisCode "1.0.12.27.0.255") 3)
    ;      block-kwh           (new AttributeAddress 3 (new ObisCode "1.0.1.29.0.255") 3)
    ;      block-kvah          (new AttributeAddress 3 (new ObisCode "1.0.9.29.0.255") 3)
    ;      res (.get conn (into-array AttributeAddress [avg-voltage]))
    ;      ]
    ;
    ;  (clojure.pprint/pprint res)
    ;
    ;  (pubsub/publish ref {:ev     :results
    ;                       :origin "client"
    ;                       :call   "get-data"
    ;                       :get-data {
    ;                                  :voltage                      (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [voltage             ])) 0))))
    ;                                  :phase-current                (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [phase-current       ])) 0))))
    ;                                  :neutral-current              (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [neutral-current     ])) 0))))
    ;                                  :signed-power-factor          (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [signed-power-factor ])) 0))))
    ;                                  :frequency                    (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [frequency           ])) 0))))
    ;                                  :apparent-power               (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [apparent-power      ])) 0))))
    ;                                  :active-power                 (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [active-power        ])) 0))))
    ;                                  :acc-active-energy            (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [acc-active-energy   ])) 0))))
    ;                                  :acc-apparent-energy          (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [acc-apparent-energy ])) 0))))
    ;                                  :acc-power-on                 (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [acc-power-on        ])) 0))))
    ;                                  :avg-voltage                  (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [avg-voltage             ])) 0))))
    ;                                  :block-kwh                    (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [block-kwh           ])) 0))))
    ;                                  :block-kvah                   (.toString (.value (.resultData (.get (.get conn (into-array AttributeAddress [block-kvah          ])) 0))))
    ;                                  }}))
    (catch TimeoutException e (publish-error ref (str "read - timeout: " (.getMessage e))))))

; 0.0.96.1.0.255 - meter serial number
; 0.0.96.1.1.255 - manfucatorer name
; 1.0.0.2.0.255  - firmware revision
; 0.0.94.91.9.255- meter-type
; 8/0.0.1.0.0.255/2-datetime
(defn handle-get-info [ref subscriber]
  (try
    (let [conn (connection/get ref)
          sn (new AttributeAddress 1 (new ObisCode "0.0.96.1.0.255") 2)
          mf (new AttributeAddress 1 (new ObisCode "0.0.96.1.1.255") 2)
          fw (new AttributeAddress 1 (new ObisCode "1.0.0.2.0.255") 2)
          dt (new AttributeAddress 8 (new ObisCode "0.0.1.0.0.255") 2)
          ;results (.get conn (into-array AttributeAddress [sn mf fw dt]))
          results-sn (.get (.get conn (into-array AttributeAddress [sn])) 0)
          results-mf (.get (.get conn (into-array AttributeAddress [mf])) 0)
          results-fw (.get (.get conn (into-array AttributeAddress [fw])) 0)
          results-dt (.get (.get conn (into-array AttributeAddress [dt])) 0)]

      (pubsub/publish ref {:ev     :results
                           :origin "client"
                           :call   "get-info"
                           :get-info {
                                       :serial-number (new String (.value (.resultData results-sn)))
                                       :firmware      (new String (.value (.resultData results-fw)))
                                       :manufactorer  (new String (.value (.resultData results-mf)))
                                       :datetime      (debug/dt-to-str results-dt)
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

(defn handle-get-eventlog [ref subscriber]

    (let [conn (connection/get ref)
          res (.get conn (into-array AttributeAddress [(new AttributeAddress 3  (new ObisCode "1.0.94.91.14.255") 2)]))




          ;instantaneous-profile        (read-obis conn 7  "1.0.94.91.0.255"   2) ; fragmentation
          ;instantaneous-scaler-profile (read-obis conn 7  "1.0.94.91.3.255"   2)
          ;block-load-profile           (read-obis conn 7  "1.0.99.1.0.255"    2) ; fragmentation
          ;block-load-scaler-profile    (read-obis conn 7  "1.0.94.91.4.255"   2)
          ;daily-load-profile           (read-obis conn 7  "1.0.99.2.0.255"    2) ; fragmentation
          ;daily-load-scaler-profile    (read-obis conn 7  "1.0.94.91.5.255"   2)
          ;billing-profile              (read-obis conn 7  "1.0.98.1.0.255"    2) ; fragmentation
          ;billing-scaler-profile       (read-obis conn 7  "1.0.94.91.6.255"   2)
          ;event0-scaler-profile        (read-obis conn 7  "1.0.94.91.7.255"   2)
          ;event0-profile               (read-obis conn 7  "0.0.99.98.0.255"   2) ; fragmentation
          ;event1-profile               (read-obis conn 7  "0.0.99.98.1.255"   2) ; fragmentation
          ;event2-profile               (read-obis conn 7  "0.0.99.98.2.255"   2)
          ;event3-profile               (read-obis conn 7  "0.0.99.98.3.255"   2) ; fragmentation
          ;event4-profile               (read-obis conn 7  "0.0.99.98.4.255"   2) ; bytebuffer
          ;name-plate-detail            (read-obis conn 7  "0.0.94.91.10.255"  2)
          ;real-time-clock              (read-obis2 conn 8  "0.0.1.0.0.255"    2 debug/dt-to-str2)
          ;association0                 (read-obis conn 15 "0.0.40.0.1.255"    2) ; bytebuffer
          ;association1                 (read-obis conn 15 "0.0.40.0.2.255"    2) ; fragmentation
          ;association2                 (read-obis conn 15 "0.0.40.0.3.255"    2)
          ]

      (clojure.pprint/pprint(.get res 0))
      ;(clojure.pprint/pprint instantaneous-profile)

      (pubsub/publish ref {:ev     :results
                                   :origin "client"
                               :call   "get-info"
                               :get-info {
                                          ;:instantaneous-profile              instantaneous-profile
                                          ;:instantaneous-scaler-profile       instantaneous-scaler-profile
                                          ;:block-load-profile                 block-load-profile
                                          ;:block-load-scaler-profile          block-load-scaler-profile
                                          ;:daily-load-profile                 daily-load-profile
                                          ;:daily-load-scaler-profile          daily-load-scaler-profile
                                          ;:billing-profile                    billing-profile
                                          ;:billing-scaler-profile             billing-scaler-profile
                                          ;:event0-scaler-profile              event0-scaler-profile
                                          ;:event0-profile                     event0-profile
                                          ;:event1-profile                     event1-profile
                                          ;:event2-profile                     event2-profile
                                          ;:event3-profile                     event3-profile
                                          ;:event4-profile                     event4-profile
                                          ;:name-plate-detail                  name-plate-detail
                                          ;:real-time-clock                    real-time-clock
                                          ;:association0                       association0
                                          ;:association1                       association1
                                          ;:association2                       association2
                                          }}))
    )


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
                  "get-data"           (handle-read ref subscriber)
                  "get-attributes"     (handle-get-eventlog ref subscriber)
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


; nameFormat() -> <Interface_Class_ID>/<OBIS_Code>/<Object_Attribute_ID>
;
;System.out.println("Enter: " + nameFormat());
;String requestParameter = inputReader.readLine();
;
;GetResult result;
;try {
;     result = callGet(requestParameter);
;     } catch (TimeoutException e) {
;                                   // TODO Auto-generated catch block
;                                   e.printStackTrace();
;                                   System.err.println("Failed to process read.")
;
;                                   return;
;                                   } catch (IllegalArgumentException e) {
;                                                                         System.err.printf(e.getMessage());
;                                                                         return;
;                                                                         }
;AccessResultCode resultCode = result.resultCode()
;
;if (resultCode == AccessResultCode.SUCCESS) {
;                                             System.out.println("Result Code: " + result.resultCode());
;
;                                             DataObject resultData = result.resultData();
;                                                               System.out.println(resultData.toString());
;                                             }
;else {
;      System.err.printf("Failed to read. AccessResultCode: %s%n", resultCode);