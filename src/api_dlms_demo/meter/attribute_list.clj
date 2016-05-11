(ns api-dlms-demo.meter.attribute-list
  (:require [api-dlms-demo.pubsub :as pubsub]
            [clojure.core.async :as async]
            [api-dlms-demo.persistance.worker :as worker])
  (:import (org.openmuc.jdlms.interfaceclass.attribute AttributeDirectory AttributeDirectory$AttributeNotFoundException AssociationLnAttribute)
           (api_dlms_demo.meter AttributeAccessMode MethodAccessMode)
           (org.openmuc.jdlms.interfaceclass.method MethodDirectory MethodDirectory$MethodNotFoundException)
           (org.openmuc.jdlms AttributeAddress AccessResultCode ObisCode)
           (org.openmuc.jdlms.interfaceclass InterfaceClass)
           (java.io IOException)))

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

(defn get-attributes [conn]
    (let [attr (new AttributeAddress AssociationLnAttribute/OBJECT_LIST (new ObisCode "0.0.40.0.0.255"))
          results (.get (.get conn (into-array AttributeAddress [attr])) 0)
          code (.resultCode results)]

      (if (not= AccessResultCode/SUCCESS)
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





        output
      )))

(defn handle
  ([ref] (handle ref (crypto.random/hex 4)))
  ([ref target]
   (println "put OBJECT_LIST work")
   (async/>!! (deref worker/workers) [target ref [] (fn [conn] (get-attributes conn))])
   (println "work: OBJECT_LIST")

   (pubsub/publish ref {:ev     "data:read-worker"
                        :action :queue
                        :where  "attribute-list"
                        :attrs   ["2/0.0.40.0.0.255"]
                        :future target})))