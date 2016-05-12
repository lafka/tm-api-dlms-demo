(ns api-dlms-demo.persistance.worker
  (:require [clojure.core.async :as async]
            [api-dlms-demo.cloud.transport :as transport]
            [api-dlms-demo.meter.dlms :as dlms]
            [api-dlms-demo.persistance.cache :as cache]
            [api-dlms-demo.pubsub :as pubsub]
            [clojure.string :as string])
  (:import (org.openmuc.jdlms CloudConnectionBuilder AttributeAddress ObisCode ClientConnection AccessResultCode SetParameter MethodParameter)
           (java.util.concurrent TimeoutException)
           (java.util Base64 LinkedList)
           (java.nio ByteBuffer)
           (org.openmuc.jdlms.interfaceclass.attribute AssociationLnAttribute)
           (org.openmuc.jdlms.datatypes DataObject)))


(def workers (atom (async/chan 1000)))

(defn builder [ref] (new CloudConnectionBuilder dlms/settings (transport/-transport-factory ref nil)))

(defn fmt [val [iface obis attr] last]
  (cond
    (or
      (and (= 3 iface) (= "0.0.0.1.2.255" obis) (= 2 attr) )
      (and (= 8 iface) (= "0.0.1.0.0.255" obis) (= 2 attr) ))

      (let [year (.getShort (ByteBuffer/wrap (byte-array (take 2 val))))
            month (get val 2)
            day (get val 3)
            hour (get val 5)
            minute (get val 6)
            seconds (get val 7)]

        {:value (str year "-" month "-" day "T" hour ":" minute ":" seconds) :raw (.encodeToString (Base64/getEncoder) last)})

    (= (type val) LinkedList)
      {:value (.toString val) :raw (.encodeToString (Base64/getEncoder) last)}
    true
      {:value val :raw (.encodeToString (Base64/getEncoder) last)}
    ))

(defn read-code [conn iface obis attr]
    (let [obis (new ObisCode obis)
          attr (new AttributeAddress iface obis attr)
          res (.get (.get conn (into-array AttributeAddress [attr])) 0)]

      (if (not= AccessResultCode/SUCCESS (.resultCode res))
        (string/join "/" ["AccessResultCode" (.name (.resultCode res))])
        (.value (.resultData res)))
    ))

(defn publish-update [update ref target [iface obis attr]]
  (pubsub/publish ref {:ev     "data:update"
                       :attr   (string/join "/" [iface obis attr])
                       :data   update
                       :future target
                       :where  "worker/read-codes"
                       }))

(defn publish-read-init [ref target [iface obis attr]]
  (pubsub/publish ref {:ev     "data:read-init"
                       :attr   (string/join "/" [iface obis attr])
                       :future target
                       :where  "worker/read-codes"
                       }))

(defn publish-write-init [ref target [iface obis attr value]]
  (pubsub/publish ref {:ev     "data:write-init"
                       :attr   {(string/join "/" [iface obis attr]) value}
                       :future target
                       :where  "worker/write-codes"
                       }))

(defn handle
  ([ref attrs] (handle ref attrs (crypto.random/hex 4)))
  ([ref attrs target] (handle ref attrs target true))
  ([ref attrs target notify]
   (println "put work")
   (async/>!! @workers [target ref attrs])
   (println "work")

    (pubsub/publish ref {:ev     "data:read-worker"
                         :action :queue
                         :where  "worker/read"
                         :attrs   (map #(clojure.string/join "/" %) attrs)
                         :future target})))

(defn write
  ([ref attrs] (write ref attrs (crypto.random/hex 4)))
  ([ref attrs target]
   (println "putting write work " target)
   (async/>!! @workers [target ref attrs :write])
   (println "waiting for write work to be picked up " target)

   (pubsub/publish ref {:ev     "data:write-worker"
                        :action :queue
                        :where  "worker/write"
                        :attrs   (map #(clojure.string/join "/" (drop-last 1 %)) attrs)
                        :future target})

   ))

(defn write-code [conn ref target iface obis attr type value]
  (println "write: " iface "/" obis "/" attr " :: " type ": " value)
  (let [val (case type
              "bool" (DataObject/newBoolData (Boolean/parseBoolean value))
              "bit" (DataObject/newInteger16Data (Short/parseShort value))
              "int" (DataObject/newInteger16Data (Short/parseShort value))
              "float" (DataObject/newFloat32Data (Float/parseFloat value)))
        obisobj (new ObisCode obis)
        attrobj (new AttributeAddress iface obisobj attr)
        setter (new SetParameter attrobj val)]

    (.set conn (into-array SetParameter [setter]))

    (publish-update value ref target [iface obis attr])

    ;; re-read the data and publish
    (-> conn
        (read-code iface obis attr)
        (fmt [iface obis attr] (.lastData conn))
        (cache/add ref [iface obis attr])
        (publish-update ref target [iface obis attr]))

    ))


(defn exec-code [conn iface obis attr]
  (println "exec: " iface "/" obis "/" attr)
  (let [obis (new ObisCode obis)
        setter (new MethodParameter iface obis attr)]

    (.action conn (into-array MethodParameter [setter]))))

;res (.get (.get conn (into-array AttributeAddress [attr])) 0)]


;(defn read-code [conn iface obis attr]
;  (let [obis (new ObisCode obis)
;        attr (new AttributeAddress iface obis attr)
;        res (.get (.get conn (into-array AttributeAddress [attr])) 0)]
;
;    (if (not= AccessResultCode/SUCCESS (.resultCode res))
;      (string/join "/" ["AccessResultCode" (.name (.resultCode res))])
;      (.value (.resultData res)))
;    ))

(defn write-codes [conn ref [ [iface obis attr type value] & rest] target]
  (publish-write-init ref target [iface obis attr value])

  (case type
    "exec" (exec-code conn iface obis attr)
    (write-code conn ref target iface obis attr type value)
    )
  ;(publish-purge ref target [iface obis attr])f

  (if (not= rest nil) (write-codes conn ref rest target)) )

(defn read-codes  [conn ref [ [iface obis attr n-try] & rest] target]
  (let [n-try (or n-try 0)]
      (try
        (set! (.lastData conn) (byte-array []))

        (publish-read-init ref target [iface obis attr])

        (-> conn
            (read-code iface obis attr)
            (fmt [iface obis attr] (.lastData conn))
            (cache/add ref [iface obis attr])
            (publish-update ref target [iface obis attr]) )

        (if (not= nil rest)
          (read-codes conn ref rest target))

        true

        (catch TimeoutException e
          (do
            (cache/add "timeout" ref [iface obis attr])
            (publish-update "timeout" ref target [iface obis attr])
            ; initiate new connection
            (println "got a timeout.... continuing with " (count rest) n-try)


            (if (< n-try 1)
              (handle ref (cons [iface obis attr (+ 1 n-try)] rest) target))

            false
            ))

        (catch Exception e
          (do
            (println "error reading attr: " iface "/" obis "/" attr)
            (clojure.stacktrace/print-stack-trace e)

            (cache/add (str (type e) (.getMessage e)) ref [iface obis attr])
            (publish-update (str (type e) (.getMessage e)) ref target [iface obis attr])

            (pubsub/publish ref {:ev     :error
                                 :error  {(type e) (.getMessage e)}
                                 :where  "worker/read"
                                 :future target
                                 }))

          (if (not= nil rest)
            (read-codes conn ref rest target))
        )
      )))

(defn with-conn [ref csp]
    (let [conn (.buildLnConnection2 (builder ref))]
      (CloudConnectionBuilder/connect conn)
      (clojure.pprint/pprint csp)
      (apply csp [conn])))

(defn init []
  (async/go-loop []
    (let [[target ref attrs & [fun]] (async/<!! @workers)]
      (try

        (with-conn ref
                   (cond
                     (= :write fun)
                       (fn [conn]
                         (if (write-codes conn ref attrs target)
                           (pubsub/publish ref {:ev     "data:write-worker"
                                                :action :done
                                                :where  "worker/read"
                                                :future target}))

                         (.disconnect conn true))

                     (or (= nil fun) (= :read fun))
                       (fn [conn]
                         (if (read-codes conn ref attrs target)
                           (pubsub/publish ref {:ev     "data:read-worker"
                                                :action :done
                                                :where  "worker/read"
                                                :future target}))

                         (.disconnect conn true))

                     true fun
                     ))

        (catch Exception e
          (do
            (clojure.stacktrace/print-stack-trace e)

            (pubsub/publish ref {:error {(type e) (.getMessage e)}
                                 :where "worker/read"
                                 :future target
                                 }))))

      (println "handler/loop work was done")
      (recur)
      )))
;
;(defn init []
;  (async/go-loop []
;    (let [[target ref attrs] (async/<!! @workers)]
;      (try
;        (let [conn (.buildLnConnection2 (builder ref))]
;          (CloudConnectionBuilder/connect conn)
;          (let [done? (read-codes conn ref attrs target)]
;            (if done?
;              (pubsub/publish ref {:ev     "data:read-worker"
;                                   :action :done
;                                   :where  "worker/read"
;                                   :future target}))
;
;            (.disconnect conn true)
;          ))
;
;        (catch Exception e
;          (do
;            (clojure.stacktrace/print-stack-trace e)
;
;            (pubsub/publish ref {:error {(type e) (.getMessage e)}
;                                 :where "worker/read"
;                                 :future target
;                                 }))))
;
;      (println "handler/loop work was done")
;      (recur)
;      )))
