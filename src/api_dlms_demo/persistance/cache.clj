(ns api-dlms-demo.persistance.cache
  (:require [api-dlms-demo.pubsub :as pubsub]))



(def cache (atom {}))

(defn store-list [ref]
  (filter #(= ref (first (first %))) @cache))

(defn store-get [ref iface obis attr at]
  (let [val (@cache [ref iface obis attr])]
    (if (= nil at)
      val
      (get val at))))


(defn store-put [ref iface obis attr item]
  (swap! cache (fn [old] (assoc old [ref iface obis attr] (cons item (old [ref iface obis attr]))))))


(defn add [data ref [iface obis attr]]
  (store-put ref iface obis attr data)
  (store-get ref iface obis attr nil)
  )


(defn read [ref [iface obis attr] resp]
  (let [val (store-get ref iface obis attr nil)]
    (resp {(str iface "/" obis "/" attr) val})))

(defn list [ref]
  (pubsub/publish ref {:ev     "data:list"
                       :where  "cache/list"
                       :data   (store-list ref)
                       :ref    (str ref)
                       }))