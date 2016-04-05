(ns lens.database
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [datomic.api :as d]
            [lens.logging :as log]))

(defn- info [msg]
  (log/info {:component "Database" :msg msg}))

(defrecord Database [db-uri conn]
  Lifecycle
  (start [creator]
    (info (str "Start database on " db-uri))
    (assoc creator :conn (d/connect db-uri)))
  (stop [creator]
    (info "Stop database.")
    (assoc creator :conn nil)))

(defn new-database [db-uri]
  (map->Database {:db-uri db-uri}))
