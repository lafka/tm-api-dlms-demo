(ns api-dlms-demo.store.device
  (:refer-clojure :exclude (list get)))

(def devices (atom {}))

(defn list [nid]
  (into {} (filter #(= (clojure.core/get (second %) :network) nid) @devices)))

(defn get [key]
  (@devices (keyword key)))

(defn put [key attrs]
  (swap! devices assoc key attrs))

(defn delete [key]
  (let [old-attrs (get key)]
    (swap! devices dissoc key)
    old-attrs))

(defn populate-item [dev]
  (let [nid (clojure.core/get dev :network)
        key (clojure.core/get dev :key)]

    (put (str nid "/" key) dev)))

(defn populate [[head & rest]]
  (cond
    (= nil head) nil
    true (do
           (populate-item head)
           (populate rest))
    ))

; (into {} (filter #(> (= (get (second %) :network) "1")) a))