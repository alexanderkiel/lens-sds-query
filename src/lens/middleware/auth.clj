(ns lens.middleware.auth
  (:use plumbing.core)
  (:require [clojure.core.cache :as cache]
            [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [lens.oauth2 :as oauth2]))

(defnk get-token [headers]
  (when-let [authorization (headers "authorization")]
    (let [[scheme token] (str/split authorization #" ")]
      (when (= "bearer" (.toLowerCase scheme))
        token))))

(def unauthorized
  {:status 401
   :body "Unauthorized"})

(defn wrap-auth [handler cache token-introspection-uri]
  (let [introspect #(<!! (oauth2/introspect token-introspection-uri %))]
    (fn [req]
      (if-let [token (get-token req)]
        (let [cache (swap! cache #(cache/through introspect % token))]
          (if-letk [[user-info] (get cache token)]
            (handler (assoc req :user-info user-info))
            unauthorized))
        unauthorized))))
