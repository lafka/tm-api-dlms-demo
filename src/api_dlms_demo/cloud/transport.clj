(ns api-dlms-demo.cloud.transport
  (:require [clojure.core.async :as async :refer [chan go >!! <!!]]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [api-dlms-demo.pubsub :as pubsub]
            [api-dlms-demo.meter.hdlc :as hdlc]
            [api-dlms-demo.meter.hdlc.debug :as debug]
            [http.async.client :as http])
  (:import (org.openmuc.jdlms HexConverter)
           (java.util Arrays Base64)
           (api_dlms_demo.cloud CloudTransportLayerConnection ICloudTransportLayerConnection)
           (java.io IOException EOFException)
           (org.openmuc.jdlms.internal.transportlayer.hdlc FrameType)))


(def cliopts (atom {:remote "http://http.stage.highlands.tiny-mesh.com/v2",
                    :user "dev@nyx.co",
                    :password "1qaz!QAZ",
                    :verbosity 0}))


(defn http-post [path body options]
  (let [remote (get options :remote)
        user (get options :user)
        password (get options :password)
        endpoint (str remote path)]

    (with-open [client (http/create-client )]
;      (println "transport/post " endpoint body)
      (let [resp (http/POST client endpoint :body body :auth {:type :basic :user user :password password :preemptive true})
            buf (-> resp
                http/await
                http/string)]

        (json/read-str buf :key-fn #(keyword %))))))


(defn buffer [buf state]
  (swap! state (fn [s] (assoc s :buffer (byte-array (concat (s :buffer) buf))))))

(defn handle [ref buf pipeline listener state]
  (let [frame (hdlc/decode buf)]

    (swap! state assoc :buffer (byte-array []))

    (pubsub/publish ref {:ev     :recv
                                    :origin "transport/recv"
                                    :hdlc   (debug/debug frame)
                                    :raw    buf})


    (async/put! pipeline frame)

    (cond
      (= FrameType/INFORMATION (.frameType frame))
      (do
        (swap! state hdlc/inc-recv-seq)
        (.dataReceived listener
                       (byte-array (drop 3 (.informationField frame))))))
    ))

(defn open-transport [path options pipeline listener state]
  (with-open [client (http/create-client)]
    (let [url (str (get options :remote) "/messages/" path "?data.encoding=base64&date.from=NOW&stream=true&continuous=true")
          user (get options :user)
          password (get options :password)
          resp (http/stream-seq client :get url :auth {:type :basic :user user :password password :preemptive true})]

      (println "transport/stream " url)
      (pubsub/publish (keyword path) {:ev :connect :origin :cloud})
      (>!! pipeline [:continue resp])

      (doseq [s (http/string resp)]
        (cond
          (string/blank? s) (println "transport/event: keep-alive")

          s (let [data (json/read-str s :key-fn #(keyword %))
                  b64buf (get-in data [:proto/tm :data])]

              (if (= "serial" (get-in data [:proto/tm :detail]))
                (let [buf (.decode (Base64/getDecoder) b64buf)]
                  (if (= 120 (alength buf))
                    (buffer buf state)
                    (let [received (byte-array (concat (@state :buffer) buf))]
                      (println "received[" (alength received) "]:" (str "\n" (HexConverter/toHexString received)))
                      (if (= (seq (byte-array [0xff])) (seq received))
                        nil ; some random 0xff appears every now and then
                        (do
                          ; clear receive buffer, and ship the data
                          (swap! state assoc :buffer (byte-array []))
                          (handle (keyword path)
                                received
                                pipeline
                                listener
                                state))))))

                (pubsub/publish (keyword path) (merge {:ev :meta :origin "transport/cloud"} data))
                ;(.dataReceived listener buf) )
                ))))

      (println "transport/closed" path)
      )))


(def llc-req  (byte-array [0xE6 0xE6 0x00 ]))
(def llc-resp (byte-array [0xE6 0xE6 0x00 ]))

(defn -transport-factory
  [ref subscriber] ; ref :: keyword, pipeline :: pubsub/subscribe
  (let [state (atom (hdlc/init ref))
        path (subs (str ref) 1)
        pipeline (async/chan)
        options @cliopts]

    (reify ICloudTransportLayerConnection
      (startListening [this listener]
        (println "transport/open[" path "]")
        (go (open-transport path options pipeline listener state))

        ;; wait for cloud to initiate connection
        (let [[:continue resp] (async/<!! pipeline)]
          ; first update state to avoid deadlock where connect fails
          (swap! state assoc :resp resp)

          (let [ [resp newstate] (hdlc/connect this @state)]
            (case resp
              :ack (reset! state newstate)
              :disconnect (throw (EOFException. "remote sent disconnect"))
              (throw (EOFException. "hdlc connection failed")))
            )
          )

        nil)


      (send [this buf offset length]
        (.send this (Arrays/copyOfRange buf offset (+ length offset))))


      (sendraw [this buf]
        (let [buf (.encodeToString (Base64/getEncoder) buf)
              body (json/write-str {"proto/tm" {:type :command :command :serial :data buf}})]

        (http-post (str "/message/" path) body options)
        nil))


      (send [this buf]
        (let [[frame newstate] (hdlc/build-info @state (byte-array (concat llc-req buf)))
              encoded (hdlc/encode frame)]

          (reset! state newstate)

          (pubsub/publish (keyword path) {:ev     :hdlc
                                          :origin "transport/send"
                                          :hdlc   (debug/debug frame)
                                          :raw    encoded})

          (.sendraw this encoded)))


      (poll [this]
        (async/<!! pipeline))


      (close [this]
        (async/close! pipeline)
        (let [newstate (hdlc/disconnect this @state)]
          (reset! state newstate)
          (http/cancel (@state :resp)))
        nil))))