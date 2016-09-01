(ns api-dlms-demo.conn.state
  (:require [clojure.core.async :as async]))

(defn init [] (atom {:chan (async/chan)
                     :subscriptions (vector)}))

(defn get [state key] (@state key))
(defn set [state key val] (swap! state assoc key val))