(ns lens.server
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [org.httpkit.server :refer [run-server]]
            [lens.app :refer [app]]))

(defrecord Server [port thread db-creator stop-fn]
  Lifecycle
  (start [server]
    (let [opts (assoc server :conn (:conn db-creator))
          handler (app opts)]
      (assoc server :stop-fn (run-server handler opts))))
  (stop [server]
    (stop-fn)
    (assoc server :stop-fn nil)))

(defn new-server []
  (map->Server {}))
