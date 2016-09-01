(ns api-dlms-demo.persistance.cache)

(def cache (atom {}))

(defn store-list [ref]
  (filter #(= ref (first (first %))) @cache))

(defn store-get [ref attr]
  (@cache [ref attr]))

(defn store-delete [ref attr]
  (swap! cache #(dissoc % [ref attr]))
  nil)

(defn store-put [ref attr item]
  (swap! cache (fn [old] (assoc old [ref attr] item)))
  item)
