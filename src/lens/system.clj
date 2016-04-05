(ns lens.system
  (:use plumbing.core)
  (:require [com.stuartsierra.component :as comp]
            [lens.server :refer [new-server]]
            [lens.database :refer [new-database]]
            [lens.util :as util]))

(defnk new-system [lens-sds-query-version db-uri port token-introspection-uri]
  (comp/system-map
    :version lens-sds-query-version
    :port (util/parse-long port)
    :thread 4

    :database
    (new-database db-uri)

    :server
    (comp/using (new-server token-introspection-uri)
                [:version :port :thread :database])))
