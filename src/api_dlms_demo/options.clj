(ns api-dlms-demo.options)

(def store (atom {
              :remote "https://http.cloud-ng.tiny-mesh.com/v2"
              ;:user "npk.ed@flashgroup.in"
              :user "dev+2@nyx.co"
              ;:remote "http://http.stage.highlands.tiny-mesh.com/v2"
              ;:user "dev-flash@nyx.co"
              :password "BharatMata"
              :verbosity 0
              :port "3001"
            }))

(defn update [opts]
  (swap! store merge opts))

(defn get
  ([] @store)
  ([key] (@store key)))