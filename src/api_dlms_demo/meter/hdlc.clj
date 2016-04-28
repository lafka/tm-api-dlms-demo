(ns api-dlms-demo.meter.hdlc
  (:require [api-dlms-demo.pubsub :as pubsub]
            [api-dlms-demo.meter.hdlc.debug :as debug])
  (:import (org.openmuc.jdlms.internal.transportlayer.hdlc HdlcFrame HdlcAddressPair HdlcAddress FrameType HdlcParameterNegotiation)
           (java.io ByteArrayInputStream)
           (java.util Arrays Base64)))

(defn build-snrm [state poll]
  (let [src (get state :src)
        dest (get state :dest)
        ;;frame (new HdlcFrame (new HdlcAddressPair src dest) FrameType/SET_NORMAL_RESPONSEMODE)
        negotiation (get state :negotiation)
        frame (HdlcFrame/newSetNormalResponseModeFrame (new HdlcAddressPair src dest) negotiation poll)]

    [frame state]
    ))

(defn build-ack [state poll]
  (let [src (get state :src)
        dest (get state :dest)
        recvseq (get state :recvseq)
        frame (HdlcFrame/newReceiveReadyFrame (new HdlcAddressPair src dest) recvseq poll)]

    [frame state]
    ))

(defn build-disconnect [state poll]
  (let [src (get state :src)
        dest (get state :dest)
        frame (HdlcFrame/newDisconnectFrame (new HdlcAddressPair src dest) poll)]

    [frame state]
    ))

(defn build-info [state buf]
  (let [src (get state :src)
        dest (get state :dest)
        sendseq (get state :sendseq)
        recvseq (get state :recvseq)
        frame (HdlcFrame/newInformationFrame (new HdlcAddressPair src dest) sendseq recvseq buf false)]

    [frame state]))

(defn init [ref]
  {:ref ref
   :recvseq 0
   :sendseq 0
   :src (new HdlcAddress 32)   ;; is this wrong?
   :dest (new HdlcAddress 1) ;; is this right?
   :negotiation (new HdlcParameterNegotiation HdlcParameterNegotiation/MIN_INFORMATION_LENGTH HdlcParameterNegotiation/MIN_WINDOW_SIZE)
   :buffer []} )

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

    (pubsub/publish (get state :ref) {:ev     :hdlc
                                      :origin "transport/send"
                                      :hdlc   (debug/debug frame)
                                      :raw    encoded})

    (.sendraw transport encoded)

    (connect-await connect transport state) ))


(defn disconnect-await [transport state]
  (let [frame (.poll transport)]
    (case frame
      nil (publish-disconnected (get state :ref))
      (println "hdlc/disconnect got some event" (debug/frame-type frame)))))

(defn disconnect [transport state]
  (let [[frame state] (build-disconnect state true)
        encoded (encode frame)]

    (pubsub/publish (get state :ref) {:ev     :hdlc
                                      :origin "transport/send"
                                      :hdlc   (debug/debug frame)
                                      :raw    encoded})

    (.sendraw transport encoded)

    (disconnect-await transport state)

    state
    ))