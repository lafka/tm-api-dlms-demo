(ns api-dlms-demo.store.lock
  (:refer-clojure :exclude (list get))
  (:require [api-dlms-demo.store.pubsub :as pubsub]))

(def locks (atom {}))

(defn list [] @locks)

(defn get [ref]
  (@locks ref))

; validate we don't have a race-condition with concurrent lock/unlocks

(defn resolve [ref val]
  (println "lock/resolve " ref)
  (let [p (or (get ref) (promise))]
    (println "lock/resolve (OK) " ref)
    (deliver p val)
    (pubsub/publish :lock ref {:locked? (not (realized? p))})))

(defn new-lock [ref]
  (println "lock/new " ref)
  (let [p (promise)]
    (swap! locks assoc ref p)
    (pubsub/publish :lock ref {:locked? true})))

(defn new [ref]
  (let [p (get ref)]
    (cond
      (nil? p) (new-lock ref)
      (and (not= nil p) (realized? p)) (new-lock ref)
      :else (pubsub/publish :lock ref {:locked? true}))))


(defn locked? [ref]
  (if (nil? (@locks ref))
    nil
    (not (realized? (@locks ref)))))