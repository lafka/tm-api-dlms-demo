(ns api-dlms-demo.store.network
  (:refer-clojure :exclude (list get)))

(def networks (atom {}))

(defn list []
  @networks)

(defn get [nid]
  (@networks nid))

(defn put [nid attrs]
  (swap! networks assoc nid attrs))

(defn delete [nid]
  (let [old-attrs (get nid)]
    (swap! networks dissoc nid)
    old-attrs))

(defn populate-item [dev]
  (let [nid (clojure.core/get dev :network)
        key (clojure.core/get dev :key)]

    (put (str nid "/" key) dev)))

(defn populate [coll]
  (let [update (into {} (map (fn [v] [(clojure.core/get v :key) v]) coll))]
    (reset! networks update)))