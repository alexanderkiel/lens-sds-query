(ns lens.server
  (:require [clojure.core.cache :as cache]
            [com.stuartsierra.component :refer [Lifecycle]]
            [lens.app :refer [app]]
            [lens.logging :as log]
            [org.httpkit.server :refer [run-server]]))

(defn ttl-cache [ttl]
  (cache/ttl-cache-factory {} :ttl ttl))

(defn- info [m]
  (log/info (assoc m :component "Server")))

(defrecord Server [port thread database stop-fn]
  Lifecycle
  (start [server]
    (info {:action :start :port port})
    (let [auth-cache (atom (ttl-cache 60000))
          opts (assoc server :conn (:conn database) :auth-cache auth-cache)
          handler (app opts)]
      (assoc server :stop-fn (run-server handler opts))))
  (stop [server]
    (info {:action :stop})
    (stop-fn)
    (assoc server :stop-fn nil)))

(defn new-server [token-introspection-uri]
  (map->Server {:token-introspection-uri token-introspection-uri}))
