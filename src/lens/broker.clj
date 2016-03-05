(ns lens.broker
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [langohr.channel :as ch]
            [langohr.core :as rmq]
            [langohr.exchange :as ex]
            [lens.logging :as log]))

;; ---- Broker ----------------------------------------------------------------

(defn- info [msg]
  (log/info {:component "Broker" :msg msg}))

(defrecord Broker [host port username password conn exchange]
  Lifecycle
  (start [broker]
    (info (str "Start broker"))
    (let [opts (cond-> {}
                 host (assoc :host host)
                 port (assoc :port port)
                 username (assoc :username username)
                 password (assoc :password password))
          conn (rmq/connect opts)
          ch (ch/open conn)]
      (ex/declare ch exchange "topic" {:durable true})
      (assoc broker :conn conn)))

  (stop [broker]
    (info "Stop broker")
    (rmq/close conn)
    (assoc broker :conn nil)))

(defn new-broker [opts]
  (map->Broker (assoc opts :exchange "lens-sds.events")))
