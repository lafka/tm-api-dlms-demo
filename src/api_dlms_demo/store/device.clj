(ns api-dlms-demo.store.device
  (:refer-clojure :exclude (list get))
  (:require [api-dlms-demo.store.pubsub :as pubsub]))

(def devices (atom {}))

(defn list [nid]
  ; we compare nid as string
  (let [nid (name nid)]
    (into {} (filter (fn [[key _]] (= (namespace key) nid)) @devices))))

(defn get [key]
  (@devices (keyword key)))

(defn put [key attrs]
  (swap! devices assoc (keyword key) attrs)
  (pubsub/publish :device key (@devices key)))

(defn update [key attrs]
  (let [key (keyword key)]
    (swap! devices #(assoc % key (merge (% key) attrs)))
    (pubsub/publish :device key (@devices key))))


(defn delete [key]
  (let [old-attrs (get key)]
    (swap! devices dissoc key)
    old-attrs))

(defn populate-item [dev]
  (let [nid (clojure.core/get dev :network)
        key (clojure.core/get dev :key)]

    (put (keyword nid key) dev)))

(defn populate [[head & rest]]
  (cond
    (= nil head) nil
    true (do
           (populate-item head)
           (populate rest))
    ))
