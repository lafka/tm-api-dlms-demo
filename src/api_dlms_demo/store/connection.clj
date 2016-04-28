(ns api-dlms-demo.store.connection
  (:refer-clojure :exclude (list get)))

(def connections (atom {}))

(defn list []
  @connections)

(defn get [ref]
  (@connections ref))

(defn put [ref conn]
  (swap! connections assoc ref conn)
  conn)

(defn delete [ref]
  (let [old-chan (get ref)]
    (swap! connections dissoc ref)
    old-chan))