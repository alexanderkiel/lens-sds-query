(ns lens.counts
  (:require [clojure.tools.logging :as log]
            [datomic.api :as d]
            [lens.logging :refer [debug trace]]
            [lens.query :as q :refer [Query]]
            [lens.util :as u :refer [EId NonNegInt OID]]
            [schema.core :as s]))

(s/defn counts [db study-oid study-events :- #{EId}]
  (let [[[study-event-count subject-count]]
        (d/q '[:find (count ?se) (count-distinct ?sub)
               :in $ ?s-oid [?se ...]
               :where
               [?sub :subject/study-events ?se]
               [?s :study/subjects ?sub]
               [?s :study/oid ?s-oid]]
             db study-oid study-events)]
    {:study-event-count (or study-event-count 0)
     :subject-count (or subject-count 0)}))

(def Counts
  {:study-event-count NonNegInt
   :subject-count NonNegInt})

(s/defn query :- Counts [db study-oid :- OID query :- Query]
  (when-not (log/enabled? :trace)
    (debug {:t (q/t db) :study-oid study-oid :query query}))
  (let [start (System/nanoTime)
        res (->> (q/run-query db query)
                 (counts db study-oid))]
    (trace {:t (q/t db) :study-oid study-oid :query query
            :duration (u/duration start)})
    res))
