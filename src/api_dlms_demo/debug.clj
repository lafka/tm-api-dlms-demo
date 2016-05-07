(ns api-dlms-demo.debug
  (:import (org.openmuc.jdlms AccessResultCode)
           (java.nio ByteBuffer)))

(defn results [results]
  {
   :code (.toString (.resultCode results))
   :choice (if (not= nil (.resultData results)) (.toString (.resultData results)))
   }
  )


(defn dt-to-str [result]
  (let [val (.value (.resultData result))
        year (.getShort (ByteBuffer/wrap (byte-array (take 2 val))))
        month (get val 2)
        day (get val 3)
        hour (get val 5)
        minute (get val 6)
        seconds (get val 7)]

    (str year "-" month "-" day "T" hour ":" minute ":" seconds)
))


(defn dt-to-str2 [result]
  (let [val (.value result)
        year (.getShort (ByteBuffer/wrap (byte-array (take 2 val))))
        month (get val 2)
        day (get val 3)
        hour (get val 5)
        minute (get val 6)
        seconds (get val 7)]

    (str year "-" month "-" day "T" hour ":" minute ":" seconds)
    ))