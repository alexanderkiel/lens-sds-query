(ns lens.stats
  (:use plumbing.core)
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [com.stuartsierra.component :refer [Lifecycle]]
            [datomic.api :as d]
            [lens.amqp-async :as aa]
            [lens.logging :as log :refer [trace debug warn]]
            [lens.util :as u :refer [NonBlankStr NonNegInt PosInt]]
            [schema.core :as s]
            [clojure.java.io :as io]
            [shortid.core :as shortid]))

(def FormCounts
  {(s/named NonBlankStr "study-oid")
   {(s/named NonBlankStr "form-oid")
    (s/named NonNegInt "count")}})

(s/defn form-subject-counts :- (s/maybe FormCounts)
  [cache]
  (some-> (:cache cache) (deref)))

;; ---- Private ---------------------------------------------------------------

(def EId
  (s/named NonNegInt "eid"))

(defn info [msg & args]
  (log/info {:component "FormSubjectCountCache" :msg (apply format msg args)}))

(def ^:private FormCountUpdate
  [(s/one NonBlankStr "study-oid")
   (s/one NonBlankStr "form-oid")
   (s/one PosInt "subject-count")])

(s/defn update-form-count :- FormCounts
  [counts :- FormCounts [study-oid form-oid subject-count] :- FormCountUpdate]
  (update-in counts [study-oid form-oid] (fnil + 0) subject-count))

(s/defn update-form-counts :- FormCounts
  [counts :- FormCounts update-data :- [FormCountUpdate]]
  (reduce update-form-count counts update-data))

(s/defn calc-form-update-data :- [FormCountUpdate]
  [db forms :- [EId]]
  (d/q '[:find ?s-oid ?f-oid (count ?sub)
         :in $ [?f ...]
         :where
         [?f :form/oid ?f-oid]
         [?se :study-event/forms ?f]
         [?sub :subject/study-events ?se]
         [?s :study/subjects ?sub]
         [?s :study/oid ?s-oid]]
       db forms))

(defn- read-transit [payload]
  (u/read-transit (io/input-stream payload) :msgpack))

(s/defn studies [db pull-pattern]
  (->> (d/q '[:find [?s ...] :where [?s :study/oid]] db)
       (d/pull-many db pull-pattern)))

(def Entity
  (s/pred :db/id 'entity?))

(def Study
  (s/constrained Entity :study/oid 'study?))

(s/defn form-subject-counts-by-study [db study :- Study]
  (->> (d/q '[:find ?f-oid (count ?sub)
              :in $ ?s
              :where
              [?s :study/subjects ?sub]
              [?sub :subject/study-events ?se]
              [?se :study-event/forms ?f]
              [?f :form/oid ?f-oid]]
            db (:db/id study))
       (into {})))

(s/defn form-subject-counts* [db]
  (for-map [study (studies db [:db/id :study/oid])]
    (:study/oid study)
    (form-subject-counts-by-study db study)))

(defn initial-load [cache db]
  (async/thread
    (info "Start populating the cache...")
    (let [start (System/nanoTime)
          counts (form-subject-counts* db)]
      (reset! cache counts)
      (info "Finished populating the cache in %s ms" (u/duration start)))))

(defn update-loop [conn cache form-created-ch]
  (debug "Start update looping...")
  (go-loop []
    (if-let [{:keys [t] :as event} (<! form-created-ch)]
      (do (trace {:loop :update-loop :event event})
          (if t
            (let [db @(d/sync conn t)
                  tx (d/t->tx t)
                  new-form (d/q '[:find ?f . :in $ ?tx :where [?f :form/id _ ?tx]] db tx)
                  update-data (calc-form-update-data db [new-form])]
              (swap! cache update-form-counts update-data))
            (warn "Skip :form/created event without a t."))
          (recur))
      (debug "Finish update looping."))))

(defrecord FormSubjectCountCache [database broker cache form-created-ch]
  Lifecycle
  (start [this]
    (let [cache (atom {})
          form-created-ch (aa/chan (:conn broker) 64 (map read-transit)
                                   {:queue-name (str "lens-sds-query.events.form.created-" (shortid/generate 5))
                                    :consumer-tag "lens-sds-query"})]
      (initial-load cache (d/db (:conn database)))
      (update-loop (:conn database) cache form-created-ch)
      ;(aa/sub (:exchange broker) "form.created" form-created-ch)
      (assoc this :cache cache :form-created-ch form-created-ch)))
  (stop [this]
    (async/close! form-created-ch)
    (assoc this :cache nil :form-created-ch nil)))

(defn new-form-subject-count-cache []
  (map->FormSubjectCountCache {}))
