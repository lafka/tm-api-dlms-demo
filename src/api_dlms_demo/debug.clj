(ns api-dlms-demo.debug
  (:import (org.openmuc.jdlms AccessResultCode)))

(defn results [results]
  {
   :code (.toString (.resultCode results))
   :choice (if (not= nil (.resultData results)) (.toString (.resultData results)))
   }
  )
