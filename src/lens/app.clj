(ns lens.app
  (:use plumbing.core)
  (:require [bidi.ring :as bidi-ring]
            [lens.handler :as h]
            [lens.middleware.auth :refer [wrap-auth]]
            [lens.middleware.cors :refer [wrap-cors]]
            [lens.middleware.log :refer [wrap-log-errors]]))

(defnk route [token-introspection-uri :as opts]
  ["/"
   {"health" (h/health-handler opts)
    "query" {:post (wrap-auth (h/query-handler opts) token-introspection-uri)}}])

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
