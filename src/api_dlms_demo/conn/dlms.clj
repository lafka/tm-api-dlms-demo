(ns api-dlms-demo.conn.dlms
  (:require [api-dlms-demo.persistance.net-connectivity :as net-connectivity]
            [api-dlms-demo.conn.dlms-actions :as dlms-actions]
            [api-dlms-demo.store.pubsub :as pubsub]
            [api-dlms-demo.persistance.cache :as cache])
  (:import (java.util.concurrent TimeoutException)))

; the  dlms handler is a per ntework queue of work DLMS actions (read, write, execute)
; there are 4 type of events supported:
; - {:action :read    :device <device> :attr <attr>}           - read a single <attr> from <sevice>
; - {:action :write   :device <device> :attr <attr> <value>}   - write some <value> to <attr>  on <device>
; - {:action :execute :device <device> :attr <attr>}           - execute <attr> method on <device>
; - {:action :cancel  :device <device> :attr <attr>}           - cancel all operations involving <attr> for <devi>
;
; The queue is a list of actions called one at a time, it will await network locks BUT IF a action is already
; in progress the action MAY be retried (depending on failure mode)

(def queues (atom {}))

(defn queue [nid & initial]
  (case (@queues nid)
    nil (swap! queues assoc nid (vec initial))
    (@queues nid)))

(defn pop-action [nid]
  (println "dlms: popping action" (first (@queues nid)) "from" nid)
  (case (@queues nid)
    nil []
    (do
      (swap! queues #(assoc % nid (vec (rest (% nid)))))
      (pubsub/publish :queue (keyword nid) {:queue (@queues nid)}))
    ))

(defn put-action [nid action]
  (println "dlms: appending action" action "in " nid)
  (let [q (queue nid)]
    (case (some #{action} q)
      nil (do
            (swap! queues #(assoc % nid (concat (% nid) [action])))
            (pubsub/publish :queue (keyword nid) {:queue (@queues nid)}))
      :exists)))

(defn cancel-action [nid action]
  (swap! queues #(assoc %
                  nid
                  (filter (fn [e] (not= e action))
                          (% nid))))
  (println "dlms: canceling action" action "in" nid)
  (pubsub/publish :queue (keyword nid) {:queue (@queues nid)}))

(defn worker [nid retries]
  (println "dlms: picking work" (first (@queues nid)))
  (if-let [item (first (@queues nid))]
    (try
      (case (first item)
        "read"    (dlms-actions/handle-read    item)
        "write"   (dlms-actions/handle-write   item)
        "execute" (dlms-actions/handle-execute item)
        "exec"    (dlms-actions/handle-execute item)
        nil        :empty
        :ok)

      (catch TimeoutException _e
        (println "dlms: action timed out...." item)
        (apply dlms-actions/return-error (concat item [(str "RETRYING: `" (first item) "` timed out... retrying #" retries)]))
        :retry
      ))))

(defn work [nid & [retry initial]]
  (println "dlms: handeling work.... ")
  ; start working! ensure `initial` is added to queue
  (queue nid initial)

  (if (and (not= nil retry) (> retry 1))
    ; skip it
    (do
      (println "dlms: skipping after > 1 retries")
      (apply dlms-actions/return-error (concat (first (@queues nid))
                                               [(str "FAILED: `" (first (first (@queues nid))) "` timed out... skipping after " retry " retries")]))
      (pop-action nid)
      (work nid 0))

    ; continue
    (do
      (println "dlms: waiting for lock release" (@queues nid))
      (net-connectivity/await-remote-lock nid)

      (let [result (worker nid (or retry 1))]
        (case result
          :empty (do
                   (println "dlms: queue is empty!!")
                   :ok)

          :ok (do
                (println "dlms: did work... continue")
                (pop-action nid)
                (work nid 0))

          :retry (do
                   (println "dlms: work failed... retrying")
                   ; doing `(+ retry 1)` dead locks the show....
                   (work nid (+ (or retry 0) 1))
                   ))))))


(def workers (atom {}))

; check if a worker exists or start a new one
(defn start-worker [nid]
  (let [worker (@workers nid)]
    (cond
      (or (nil? worker)
          (realized? worker))

        (do
          (println "dlms: starting new worker for " nid)
          ((swap! workers #(assoc % nid (future (work nid)))) nid))
      :else (do
              (println "dlms: use existing worker for " nid)
              worker))))

; queue some data and start a ensure a worker is out there
(defn handle [{action :action} _state _resp]
    (case (first action)
      "cancel" (let [[_cancel [_op device & _rest :as action]] action
                    nid (namespace (keyword device))]

                (cancel-action nid action))

      (let [resource (keyword (second action))
            nid (namespace resource)]

        (put-action nid action)
        (start-worker nid))))

; queue some data and start a ensure a worker is out there
(defn get-queue [{nid :network} _state resp]
  (resp {:queue (or (@queues nid) []) :resource nid :type :queue}))


(defn get-attributes [{resource :resource} _state resp]
  (resp {:attributes (cache/store-list resource)
         :resource resource
         :type :dlms}))
