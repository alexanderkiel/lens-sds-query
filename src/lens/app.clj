(ns lens.app
  (:use plumbing.core)
  (:require [bidi.ring :as bidi-ring]
            [lens.handler :as h]
            [lens.middleware.cors :refer [wrap-cors]]
            [lens.middleware.log :refer [wrap-log-errors]]))

(defn- route [opts]
  ["/" {"health" (h/health-handler opts)
        "query" {:post (h/query-handler opts)}}])

(defn wrap-not-found [handler]
  (fn [req]
    (if-let [resp (handler req)]
      resp
      {:status 404})))

(defn app
  "Whole app Ring handler."
  [opts]
  (-> (bidi-ring/make-handler (route opts))
      (wrap-not-found)
      (wrap-log-errors)
      (wrap-cors)))
