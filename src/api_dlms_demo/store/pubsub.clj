(ns api-dlms-demo.store.pubsub
  (:require [clojure.core.async :as async]))

(def topic :storage)

(def publisher (async/chan))

(def publication (async/pub publisher #(:chan %)))

(defn publish [type resource data]
  (async/>!! publisher {:chan topic :type type :resource resource :data data}))

(defn subscribe [chan]
  (async/sub publication topic chan))

(defn close []
  (async/unsub-all topic))
