(ns api-dlms-demo.meter.hdlc.debug
  (:require [clojure.string :as string])
  (:import (org.openmuc.jdlms.internal.transportlayer.hdlc FrameType HdlcFrame FrameRejectReason)
           (java.util Base64)))

(defn src-addr [frame] (.toString (.sourceAddress frame)))
(defn dest-addr [frame] (.toString (.destinationAddress frame)))

(defn frame-type [frame]
  (cond
    (= FrameType/INFORMATION (.frameType frame)) :information
    (= FrameType/RECEIVE_READY (.frameType frame)) :receive-ready
    (= FrameType/RECEIVE_NOT_READY (.frameType frame)) :receive-not-ready
    (= FrameType/SET_NORMAL_RESPONSEMODE (.frameType frame)) :set-normal-responsemode
    (= FrameType/DISCONNECT (.frameType frame)) :disconnect
    (= FrameType/UNNUMBERED_ACKNOWLEDGE (.frameType frame)) :unnumbered-acknowledge
    (= FrameType/DISCONNECT_MODE (.frameType frame)) :disconnect-mode
    (= FrameType/FRAME_REJECT (.frameType frame)) :frame-reject
    (= FrameType/UNNUMBERED_INFORMATION (.frameType frame)) :unnumbered-information
    (= FrameType/ERR_INVALID_TYPE (.frameType frame)) :err-invalid-type
    ))

(defn information-field [frame] (.informationField frame))
(defn negotiation [frame] (.negotiation frame))
(defn send-seq [frame] (.sendSequence frame))
(defn recv-seq [frame] (.receiveSequence frame))
(defn segmented [frame] (.segmented frame))

(defn negotiation [frame]
  (let [negotiation (.negotiation frame)]
    {
     :transmit-window-size (.transmitWindowSize negotiation)
     :transmit-information-length (.transmitInformationLength negotiation)
     :receive-window-size (.receiveWindowSize negotiation)
     :receive-information-length (.receiveInformationLength negotiation)
     }))

(defn frame-reject [frame]
  (let [info (information-field frame)
        rejection (.rejectReasons (FrameRejectReason/decode info))]
    (map #(.name %) rejection)
    ))

(defn additional [frame]
  (case (frame-type frame)
    :unnumbered-acknowledge (negotiation frame)
    :set-normal-responsemode  (negotiation frame)
    :frame-reject (frame-reject frame)
    {}
    )
  )


(defn hexify [buf]
  (string/join " "
               (map #(format "%02x" (bit-and 255 %))
                    buf)))

(defn debug [frame]
  {
   :type              (frame-type frame)
   :src-addr          (src-addr frame)
   :dest-addr         (dest-addr frame)
   :information-field (if (not= nil (information-field frame))
                        (hexify (information-field frame))
                        nil)
   :send-seq          (send-seq frame)
   :recv-seq          (recv-seq frame)
   :segmented         (segmented frame)
   :additional        (additional frame)
   })