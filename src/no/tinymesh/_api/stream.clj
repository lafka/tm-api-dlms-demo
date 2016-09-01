(ns no.tinymesh.-api.stream
  (:require [no.tinymesh.-api.config :as config]
            [http.async.client :as http]
            [clojure.string :as string]
            [clojure.core.async :as async]
            [clojure.data.json :as json])
  (:use [http.async.client request]))

(def chunk-timeout 15000)

(defn sse-ev [s]
  (reduce
    (fn [acc a]
      (let [[k v] (string/split (string/trim a) #": ")]
        (cond
          (= k "data") (assoc acc (keyword k) (json/read-str v :key-fn #(keyword %)))
          :else (assoc acc (keyword k) v))))
    {}
    (string/split (string/trim s) #"\n")))

(defn await-pipe [client chan pipe]
  (let [input (async/alts!! [pipe (async/timeout chunk-timeout)])]
    ;(if (nil? input)
    ;  (println "api-req-stream: await-pipe -> timeout on pipe")
    ;  (println "api-req-stream: await-pipe -> took 1 from pipe"))

    (cond
      (or (nil? input) (= input :completed) (and (vector? input) (= (first input) :error) ))
        (do
          (clojure.pprint/pprint "closing stuff")
          (async/close! pipe)
          ;(http/close client)
          (async/put! chan [:close client])
          nil)

      (= input :keep-alive) (do
                              (println "api-req-stream: await-pipe -> got keepalive")
                              (await-pipe client chan pipe))

      :else
        (do
          ;(println "api-req-stream: await-pipe -> push 1 to chan")
          (async/put! chan [:ev input])
          (await-pipe client chan pipe))
      )))

(defn close [client] (http/close client))

(defn get [chan refs options]
  (let [remote (config/get :remote)]
    (with-open [client (http/create-client)]
      (let [pipe (async/chan 5)
            opts {:query {:query (string/join ";" refs)}
                  :auth (config/auth)
                  :timeout -1}]

        (println "api-req-stream:" :get (str remote "/stream"))

        (execute-request client
                         (apply prepare-request :get (str remote "/stream") (apply concat opts))
                         :part (fn [_a b]
                                 (let [buf (str b)]
                                   ;(println "push 1 to pipe (part)")
                                   (cond
                                     (string/blank? buf) (async/>!! pipe :keep-alive)
                                     :else (async/>!! pipe (sse-ev buf)))
                                   [pipe :continue]))

                         :completed (fn [_a]
                                      ;(println "push 1 to pipe (completed)")
                                      (async/>!! pipe :completed)
                                      nil)

                         :error (fn [_a _b]
                                  ;(println "push 1 to pipe (error)")
                                  (async/>!! pipe :error)))

        (println "api-req-stream: awaiting..... " chan)

        (await-pipe client chan pipe)))))