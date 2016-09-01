(ns no.tinymesh.-api.config)

(def store (atom {
                  :remote   "https://http.cloud-ng.tiny-mesh.com/v2"
                  :user     "dev+2@nyx.co"
                  :password "BharatMata"
                  }))

(defn auth
  ([] (auth {}))
  ([opts]
    (let [user (get opts :user (@store :user))
          password (get opts :password (@store :password))]

      {:type :basic :user user :password password :preemptive true})))

(defn update [opts]
  (swap! store merge opts))

(defn get
  ([] @store)
  ([key] (@store key)))