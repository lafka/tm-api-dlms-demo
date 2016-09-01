(ns api-dlms-demo.conn.store
  (:require [api-dlms-demo.store.network :as network]
            [api-dlms-demo.store.device :as device]))

(defn network [{nid :network} _state resp]
  (let [network (network/get (keyword nid))]
    (case network
      nil (resp {:error "network not found"})
      (resp {:event :storage :network network}))))

(defn networks [_body _state resp]
  (resp {:event :storage :networks (network/list)}))


(defn device [{device :device} _state resp]
  (let [device (keyword device)
        nid (keyword (namespace device))
        return (device/get device)]

    (case (network/get nid)
      nil (resp {:error "network not found"})
      (case return
        nil (resp {:error "device not found"})
        (resp {:event :storage :devices return})))))

(defn devices [{nid :network} _state resp]
  (let [nid (keyword nid)]
    (case (network/get nid)
      nil (resp {:error "network not found"})
      (resp {:event :storage :devices (device/list (keyword nid))}))))