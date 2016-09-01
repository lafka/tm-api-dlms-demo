(ns api-dlms-demo.conn.dlms-actions
  (:require [clojure.string :as string]
            [api-dlms-demo.store.pubsub :as pubsub]
            [api-dlms-demo.cloud.transport :as transport]
            [api-dlms-demo.meter.dlms :as dlms]
            [api-dlms-demo.persistance.cache :as cache])
  (:import (org.openmuc.jdlms ObisCode MethodParameter AttributeAddress SetParameter CloudConnectionBuilder)
           (org.openmuc.jdlms.datatypes DataObject)
           (java.util.concurrent TimeoutException TimeUnit)
           (org.openmuc.jdlms.internal.asn1.cosem Data$Choices)))

(defn connection [device]
  (let [settings dlms/settings
        transport (transport/-transport-factory (keyword device) nil)
        builder (new CloudConnectionBuilder settings transport)
        conn (.buildLnConnection2 builder)]

    (try
      (println "dlms-action: CONNECT" device)
      (let [f (future (CloudConnectionBuilder/connect conn))]
        (.get f (.responseTimeout dlms/settings) TimeUnit/MILLISECONDS)
        (println "dlms-action: CONNECTED" device)
        conn)

      (catch TimeoutException e
        (do
          (println "dlms-action: DISCONNECT" device "... CONNECT TIMEOUT")
          (.disconnect conn true)
          (throw e))))))

(defn parse-attr [attr]
  (let [[iface obis attr] (string/split attr #"[/@]")
        iface (Integer/parseInt iface)
        obis (new ObisCode obis)
        attr (Integer/parseInt attr) ]

    [iface obis attr]))


(defn objtype [choice]
  (cond
    (= choice Data$Choices/_ERR_NONE_SELECTED)       :undefined
    (= choice Data$Choices/NULL_DATA)                :null
    (= choice Data$Choices/ARRAY)                    :array
    (= choice Data$Choices/STRUCTURE)                :structure
    (= choice Data$Choices/BOOL)                     :bool
    (= choice Data$Choices/BIT_STRING)               :bit-string
    (= choice Data$Choices/DOUBLE_LONG)              :number
    (= choice Data$Choices/DOUBLE_LONG_UNSIGNED)     :number
    (= choice Data$Choices/OCTET_STRING)             :byte-array
    (= choice Data$Choices/VISIBLE_STRING)           :byte-array
    (= choice Data$Choices/BCD)                      :number
    (= choice Data$Choices/INTEGER)                  :number
    (= choice Data$Choices/LONG_INTEGER)             :number
    (= choice Data$Choices/UNSIGNED)                 :number
    (= choice Data$Choices/LONG_UNSIGNED)            :number
    (= choice Data$Choices/COMPACT_ARRAY)            :array
    (= choice Data$Choices/LONG64)                   :number
    (= choice Data$Choices/LONG64_UNSIGNED)          :number
    (= choice Data$Choices/ENUMERATE)                :number
    (= choice Data$Choices/FLOAT32)                  :number
    (= choice Data$Choices/FLOAT64)                  :number
    (= choice Data$Choices/DATE_TIME)                :date-time
    (= choice Data$Choices/DATE)                     :date
    (= choice Data$Choices/TIME)                     :time
    (= choice Data$Choices/DONT_CARE)                :ignore
  ))


(defn encode [obj]
  (let [type (objtype (.choiceIndex obj))]
    (case type
      :undefined  :undefined
      :null       :null
      :array      (map #(encode %) (.value obj))
      :structure  (map #(encode %) (.value obj))
      :bool       (.value obj)
      :bit-string (.value obj)
      :number     (.value obj)
      :byte-array (map #(bit-and 255 %) (.value obj))
      :date-time  (.value obj)
      :date       (.value obj)
      :time       (.value obj)
      :ignore     :undefined
  )))

(defn handle-read [[_read device k]]
  (println "dlms-action: READ " device "->" k)

  (let [[iface obis attr] (parse-attr k)
        attribute  (new AttributeAddress iface obis attr)
        conn (connection device)
        res (.get (.get conn (into-array AttributeAddress [attribute])) 0)
        raw (transport/cache-get (keyword device))
        data (.resultData res)
        value (encode data)]

    (clojure.pprint/pprint value)


    (pubsub/publish :dlms (str device "#" k) (cache/store-put device k {:operation :read
                                                                        :attr      k
                                                                        :result    value
                                                                        :data-type (objtype (.choiceIndex data))
                                                                        :raw       raw}))

    (.close conn)

    :ok))

(defn return-error [operation device k error]
  (pubsub/publish :dlms (str device "#" k) {:operation operation
                                            :attr k
                                            :error error}))


(defn data-object [type value]
  (case type
    "bool" (DataObject/newBoolData (Boolean/parseBoolean value))
    "bit" (DataObject/newInteger16Data (Short/parseShort value))
    "int" (DataObject/newInteger16Data (Short/parseShort value))
    "float" (DataObject/newFloat32Data (Float/parseFloat value))))


(defn handle-write [[_write device k [type val]]]
  (if (nil? val)
    (do
      (println "dlms-action: WRITE " device "->" k ":=" val "(skipping nil)")
      :ok)
    (do
      (println "dlms-action: WRITE " device "->" k ":=" val)

      (let [[iface obis attr] (parse-attr k)
            attribute  (new AttributeAddress iface obis attr)
            setter (new SetParameter attribute (data-object type val))
            conn (connection device)]

        (let [_res (.get (.set conn (into-array SetParameter [setter])) 0)]
          (pubsub/publish :dlms (str device "#" k) {:operation :write
                                                    :attr k
                                                    :result val
                                                    :raw (transport/cache-get (keyword device))})
          (cache/store-delete device k))

        (.close conn)

        :ok))))

(defn handle-execute [[_execute device k]]
  (println "dlms-action: EXECUTE " device "->" k)

  (let [[iface obis attr] (parse-attr k)
        action (new MethodParameter iface obis attr)
        conn (connection device)
        res (.get (.action conn (into-array MethodParameter [action])) 0)]

    (pubsub/publish :dlms (str device "#" k) {:operation :execute
                                              :attr k
                                              :result (.name (.resultCode res))})

    (.close conn)

    :ok))