(ns api-dlms-demo.pubsub
  (:require [clojure.core.async :as async]))


(def publisher (async/chan))

(def publication (async/pub publisher #(:connection %)))

(defn publish [topic data]
  (async/>!! publisher (merge {:connection topic} data)))

(defn subscribe [topic chan]
  (async/sub publication topic chan))

(defn unsubscribe [topic chan]
  (async/unsub publication topic chan))

(defn close [topic]
  (async/unsub-all topic))
