(ns lens.system
  (:use plumbing.core)
  (:require [com.stuartsierra.component :as comp]
            [lens.server :refer [new-server]]
            [lens.broker :refer [new-broker]]
            [lens.database :refer [new-database]]
            [lens.stats :refer [new-form-subject-count-cache]]
            [lens.util :as util]))

(defnk new-system [lens-sds-query-version db-uri port broker-host
                   {broker-username "guest"} {broker-password "guest"}
                   token-introspection-uri]
  (comp/system-map
    :version lens-sds-query-version
    :port (util/parse-long port)
    :thread 4

    :database
    (new-database db-uri)

    :broker
    (new-broker {:host broker-host :username broker-username
                 :password broker-password})

    :form-subject-count-cache
    (comp/using (new-form-subject-count-cache) [:database :broker])

    :server
    (comp/using (new-server token-introspection-uri)
                [:version :port :thread :database
                 :form-subject-count-cache])))
