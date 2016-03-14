(ns lens.system
  (:use plumbing.core)
  (:require [com.stuartsierra.component :as comp]
            [lens.server :refer [new-server]]
            [lens.broker :refer [new-broker]]
            [lens.datomic :refer [new-database-creator]]
            [lens.stats :refer [new-form-subject-count-cache]]
            [lens.util :as util]))

(defnk new-system [lens-sds-query-version db-uri port broker-host
                   token-introspection-uri]
  (comp/system-map
    :version lens-sds-query-version
    :port (util/parse-long port)
    :thread 4

    :db-creator
    (new-database-creator db-uri)

    :broker
    (new-broker {:host broker-host})

    :form-subject-count-cache
    (comp/using (new-form-subject-count-cache) [:db-creator :broker])

    :server
    (comp/using (new-server token-introspection-uri)
                [:version :port :thread :db-creator
                 :form-subject-count-cache])))
