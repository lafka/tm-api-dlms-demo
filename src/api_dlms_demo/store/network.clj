(ns api-dlms-demo.store.network
  (:refer-clojure :exclude (list get))
  (:require [api-dlms-demo.store.pubsub :as pubsub]
            [api-dlms-demo.store.lock :as lock]))

(def networks (atom {}))

(defn project [nid net]
  (println "network/project" nid)
  (assoc net :locked? (lock/locked? nid)))

(defn list []
  (into {} (map (fn [ [nid net]] [nid (project nid net)]) @networks)))

(defn get [nid]
  (let [net (@networks nid)]
    (if (nil? net)
      nil
      (project nid net))))

(defn put [nid attrs]
  (swap! networks assoc nid attrs)
  (pubsub/publish :network nid (project nid (@networks nid))))

(defn delete [nid]
  (let [old-attrs (get nid)]
    (swap! networks dissoc nid)
    old-attrs))

(defn populate [coll]
  (let [update (into {} (map (fn [v] [(keyword (clojure.core/get v :key)) v]) coll))]
    (map (fn [net] (pubsub/publish :network (clojure.core/get net :key) (project (clojure.core/get net :key) net))) update)
    (reset! networks update)))