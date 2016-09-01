(ns api-dlms-demo.util
  (:require [clojure.core.async :as async])
  (:import (java.util Random)))


(defn backoff [time rate max fun pred?]
  (if (>= time max)
    (fun) ; if max is reached, just keep
    (do
      (let [ret (pred? (fun))]
        (if (not= nil ret)
          ret
          (do
            (println "util/backoff: waiting for " time "ms")
            (Thread/sleep time)
            (backoff (min max (* time rate)) rate max fun pred?)))))))

(defn forever [fun]
  (async/go-loop []
      (println "util/forever: re-iterating")
      (fun)
      (recur)))

(def random (Random.))

(def chars
   (map char (concat (range 48 58) (range 66 92) (range 97 123))))

(defn random-char []
  (nth chars (.nextInt random (count chars))))

(defn random-string [length]
  (apply str (take length (repeatedly random-char))))