(ns lens.handler
  (:use plumbing.core)
  (:require [clojure.tools.logging :as log]
            [datomic.api :as d]
            [om.next.server :as om]
            [lens.logging :refer [info debug trace]]
            [lens.query :as q :refer [Query]]
            [lens.stats :as stats]
            [lens.util :as u :refer [NonBlankStr NonNegInt OID]]
            [schema.core :as s :refer [Any Str]])
  (:refer-clojure :exclude [read]))

;; ---- Health ----------------------------------------------------------------

(defn health-handler [_]
  (fn [_]
    {:status 200
     :body "OK"}))

;; ---- Current T -------------------------------------------------------------

(defnk current-t-handler [conn]
  (fn [_]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (str (d/basis-t (d/db conn)))}))

;; ---- Query -----------------------------------------------------------------

(defn- write-transit [o]
  (u/write-transit :json {} o))

(defn- wrap-transit [handler]
  (fnk [body :as request]
    (let [body (u/read-transit body :json)]
      (if (instance? Exception body)
        {:status 422
         :body (.getMessage body)}
        (-> (assoc request :body body)
            (handler)
            (assoc-in [:headers "Content-Type"] "application/transit+json")
            (update :body write-transit))))))

(defmulti read
          "Like om read. The env contains :conn and :user-info."
          (fn [_ k _] k))

(s/defn query-handler
  "Handler for Om.next style queries.

  Look at the read functions for the available query keys."
  [opts :- {:conn Any Any Any}]
  (wrap-transit
    (fnk [{user-info nil} body]
      (if user-info
        (let [parser (om/parser {:read read})
              env (assoc opts :user-info user-info)]
          (try
            {:status 200
             :body (parser env body)}
            (catch Exception e
              {:status (get (ex-data e) :status 500)
               :body {:error (.getMessage e)}})))
        {:status 401
         :body "Unauthorized"}))))

;; ---- Form Subject Counts ---------------------------------------------------

(defn- check-study-oid [study-oid]
  (when (s/check OID study-oid)
    (throw (ex-info "Invalid or missing Study OID." {:status 400}))))

(defmethod read :form-subject-counts
  [{:keys [form-subject-count-cache]} _ {:keys [study-oid]}]
  (check-study-oid study-oid)
  (when-let [counts (get (stats/form-subject-counts form-subject-count-cache) study-oid)]
    {:value counts}))

;; ---- Query -----------------------------------------------------------------

(defn- check-t [t]
  (when (s/check (s/maybe NonNegInt) t)
    (throw (ex-info "Invalid T." {:status 400}))))

(defn- check-query [query]
  (when-let [e (s/check Query query)]
    (throw (ex-info (str "Invalid or missing Query: " (pr-str e))
                    {:status 400}))))

(defn- sync-db [conn t]
  (if-let [db (if t (deref (d/sync conn t) 100 nil) (d/db conn))]
    db
    (throw (ex-info (str "Unable to sync on t: " t) {:status 422}))))

(defmethod read :query
  ;; Queries for study-event count and subject count.
  [{:keys [conn user-info]} _ {:keys [t study-oid query]}]
  (check-t t)
  (check-study-oid study-oid)
  (check-query query)
  (when-not (log/enabled? :trace)
    (debug {:query :query
            :username (:username user-info)
            :params
            {:t t
             :study-oid study-oid
             :query query}}))
  (let [start (System/nanoTime)
        res {:value (q/query (sync-db conn t) study-oid query)}]
    (trace {:query :query
            :username (:username user-info)
            :params
            {:t t
             :study-oid study-oid
             :query query}
            :duration (u/duration start)})
    res))
