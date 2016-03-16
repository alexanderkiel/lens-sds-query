(ns lens.handler
  (:use plumbing.core)
  (:require [datomic.api :as d]
            [om.next.server :as om]
            [lens.logging :refer [info]]
            [lens.query :as q :refer [Query]]
            [lens.stats :as stats]
            [lens.util :as u :refer [NonNegInt NonBlankStr]]
            [schema.core :as s :refer [Str]])
  (:refer-clojure :exclude [read]))

;; ---- Health ----------------------------------------------------------------

(defn health-handler [_]
  (fn [_]
    {:status 200
     :body "OK"}))

;; ---- Query ---------------------------------------------------------------

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
  "Like om read. The env contains :user-info."
  (fn [_ k _] k))

(defn query-handler [opts]
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

(defmethod read :form-subject-counts
  [{:keys [form-subject-count-cache]} _ {:keys [study-oid]}]
  (when-let [counts (get (stats/form-subject-counts form-subject-count-cache) study-oid)]
    {:value counts}))

(defn- check-t [t]
  (when (s/check (s/maybe NonNegInt) t)
    (throw (ex-info "Invalid T." {:status 400}))))

(defn- check-study-oid [study-oid]
  (when (s/check NonBlankStr study-oid)
    (throw (ex-info "Invalid or missing Study OID." {:status 400}))))

(defn- check-query [query]
  (when-let [e (s/check Query query)]
    (throw (ex-info (str "Invalid or missing Query: " (pr-str e))
                    {:status 400}))))

(defmethod read :query
  [{:keys [conn]} _ {:keys [t study-oid query]}]
  (check-t t)
  (check-study-oid study-oid)
  (check-query query)
  (if t
    (if-let [db (deref (d/sync conn t) 100 nil)]
      {:value (q/query (d/as-of db t) study-oid query)}
      (throw (ex-info (str "Unable to sync on t: " t) {:status 422})))
    {:value (q/query (d/db conn) study-oid query)}))
