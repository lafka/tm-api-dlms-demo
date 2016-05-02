(ns api-dlms-demo.meter.hdlc
  (:require [api-dlms-demo.pubsub :as pubsub]
            [api-dlms-demo.meter.hdlc.debug :as debug]
            [clojure.core.async :as async])
  (:import (org.openmuc.jdlms.internal.transportlayer.hdlc HdlcFrame HdlcAddressPair HdlcAddress FrameType HdlcParameterNegotiation)
           (java.io ByteArrayInputStream)
           (java.util Arrays Base64)
           (api_dlms_demo.meter ParameterNegotiation)))



(defn init [ref]
  (let [
        recv-info-size 129
        send-info-size 129
        send-window-size 1
        recv-window-size 1
        negotiation (new ParameterNegotiation recv-info-size recv-window-size send-info-size send-window-size)]
        ;negotiation (new HdlcParameterNegotiation 128 1)]
    {:ref         ref
     :recvseq     0
     :sendseq     -1
     :src         (new HdlcAddress 32)   ;; is this wrong?
     :dest        (new HdlcAddress 1) ;; is this right?
     :negotiation negotiation
     :queue       (async/chan)
     :buffer      (byte-array [])  ; input buffer used block responses from tinymesh
     } ))


; working:  7e a0 07 03 41 93 5a 64 7e
; n-workng: 7e a0 10 03 41 93 da f0 81 80 04   06 02 01 00       67 67 7e
; n-wrkng2: 7e a0 0f 03 41 93 82 81 81 80 03   06 01 ff          1b ed 7e



(defn inc-recv-seq [state]
  (assoc state :recvseq (mod (+ (state :recvseq) 1) 8)))

(defn inc-send-seq [state]
  (assoc state :sendseq (mod (+ (state :sendseq) 1) 8)))


; all functions  below here, except init/1, returns `[_any newstate]`


(defn build-snrm [state poll]
  (let [frame       (HdlcFrame/newSetNormalResponseModeFrame (new HdlcAddressPair
                                                                  (state :src)
                                                                  (state :dest))
                                                             (state :negotiation)
                                                             poll)]
    [frame state] ))

(defn build-ack [state poll]
  (let [frame   (HdlcFrame/newReceiveReadyFrame (new HdlcAddressPair
                                                     (state :src)
                                                     (state :dest))
                                                (state :recvseq)
                                                poll)]
    [frame state]
    ))

(defn build-disconnect [state poll]
  (let [frame (HdlcFrame/newDisconnectFrame (new HdlcAddressPair
                                                 (state :src)
                                                 (state :dest))
                                            poll)]

    [frame state]
    ))

(defn build-info [state buf]
  (let [state (inc-send-seq state)
        frame   (HdlcFrame/newInformationFrame (new HdlcAddressPair
                                                    (state :src)
                                                    (state :dest))
                                               (state :sendseq)
                                               (state :recvseq)
                                               buf
                                               false)]
    [frame state]))


(defn encode [frame] (.encodeWithFlags frame))
(defn decode [buf]
  (let [newbuf (Arrays/copyOfRange buf 1 (- (alength buf) 2))]
    (HdlcFrame/decode (new ByteArrayInputStream newbuf)))
  )


(defn publish-disconnected [ref] (pubsub/publish ref {:ev :disconnected :origin "hdlc/layer"}))
(defn publish-connecting   [ref] (pubsub/publish ref {:ev :connecting   :origin "hdlc/layer"}))


(defn connect-await [connect transport state]
  (let [frame (.poll transport)]
    (if (not= nil frame)
      (cond
        (= FrameType/UNNUMBERED_ACKNOWLEDGE (.frameType frame)) [:ack state]
        (= FrameType/DISCONNECT_MODE (.frameType frame)) [:disconnect state]
        true (connect transport state))
      )))

(defn connect [transport state]
  (let [[frame state] (build-snrm state true)
        encoded (encode frame)]

    (pubsub/publish (state :ref) {:ev     :hdlc
                                      :origin "transport/send"
                                      :hdlc   (debug/debug frame)
                                      :raw    encoded})

    (.sendraw transport encoded)

    (connect-await connect transport state) ))


(defn disconnect-await [transport state]
  (let [frame (.poll transport)]
    (case frame
      nil (do
            (async/close! (state :queue))
            (publish-disconnected (state :ref))
            [:closed state])
      (do
        (println "hdlc/disconnect got some event" (debug/frame-type frame))
        [frame state]
        ))))

(defn disconnect [transport state]
  (let [[frame state] (build-disconnect state true)
        encoded (encode frame)]


    (pubsub/publish (state :ref) {:ev     :hdlc
                                  :origin "transport/send"
                                  :hdlc   (debug/debug frame)
                                  :raw    encoded})

    (.sendraw transport encoded)

    (let [[_any state] (disconnect-await transport state)]
      state)))