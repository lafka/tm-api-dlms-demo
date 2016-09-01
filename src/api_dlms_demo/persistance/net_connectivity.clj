(ns api-dlms-demo.persistance.net-connectivity
  (:require [clojure.core.async :as async]
            [no.tinymesh.-api.stream :as api-stream]
            [api-dlms-demo.store.lock :as lock]
            [api-dlms-demo.store.network :as networks]
            [api-dlms-demo.store.device :as device]
            [api-dlms-demo.options :as options]
            [clojure.string :as string])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))

(defn iso8601-to-datetime [dt]
  (let [dt (string/replace dt #"Z$" "+0000")]
    (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSZ") dt)))

(defn connected? [[_key {meta :meta type :type _key :key}]]
  (let [connected (get meta :chan/connected)
        disconnected (get meta :chan/disconnected)]

    (cond
      (not= "gateway" type) false
      (and (= nil connected)    (= nil disconnected)) false
      (and (not= nil connected) (= nil disconnected)) true
      :else (.after (iso8601-to-datetime connected) (iso8601-to-datetime disconnected))
      )))


(defn lock-remote [nid]
  (lock/new nid))

(defn unlock-remote [nid]
  (lock/resolve nid nil))

(defn await-remote-lock [nid]
  (let [nid (keyword nid)
        lock (lock/get nid)]
    (case lock
      nil nil
      @lock)))

(defn aquire-locks [nid items]
  (doall (map (fn [{devices :devices}]
                (println "net-connectivity/aquire-locks" nid (some connected? devices))
                (cond
                   (some connected? devices) (unlock-remote nid)
                   :else (lock-remote nid))
                devices)
       items)))


(defn open-stream [gwrefs options]
  (println "gw-stream/open" gwrefs (-> (Date.) .getTime (quot 1000)))

  (let [chan (async/chan)
        future (future (api-stream/get chan gwrefs options))]

    (loop [[t ev] (async/<!! chan)]
      ;(println "net-connectivity: got resp from stream" t)

      (case t
        :close (do
                 (api-stream/close ev)
                 (println "net-connectivity: chan received :close, try deref")
                 @future
                 (println "net-connectivity: closed...")
                 nil)

        nil (do
              (println "net-connectivity: chan received nil, should close???")
              @future
              (println "net-connectivity: closed...")
              nil)

        :ev (let [ev (first ev)
                  event (get ev :event)
                  {meta :meta id :_id} (get ev :data)
                  connected (get meta :chan/connected)
                  disconnected (get meta :chan/disconnected)]

              (if (and (= "device" event) (or (not= nil connected)
                                              (not= nil disconnected)))
                (let [dev (keyword id)
                      nid (keyword (namespace dev))]


                  (device/update dev {:meta (merge ((device/get dev) :meta) meta)})
                  (aquire-locks nid [(assoc (networks/get nid) :devices (device/list nid))])))

              (recur (async/<!! chan))
              ))
    )



    ))

(defn gw-resources []
  (apply concat
         (map (fn [[nid _net]]
                (reduce
                  (fn
                    [acc [res {t :type}]]
                      (cond
                        (= t "gateway") (concat [(str "device/" (namespace res) "/" (name res))] acc)
                        :else acc))
                  []
                  (api-dlms-demo.store.device/list nid)))
              (api-dlms-demo.store.network/list))))


(defn ensure-network-streams []
  (println "net-connectivity/ensure-network-streams")
  (let [res (open-stream (gw-resources) (options/get))]
    (println "net-connectivity/ensure-network-streams returned")
    res))
