(ns lens.middleware.auth
  (:use plumbing.core)
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [lens.oauth2 :as oauth2]))

(defnk get-token [headers]
  (when-let [authorization (headers "authorization")]
    (let [[scheme token] (str/split authorization #" ")]
      (when (= "bearer" (.toLowerCase scheme))
        token))))

(defn wrap-auth [handler token-introspection-uri]
  (let [oauth2-introspect (partial oauth2/introspect token-introspection-uri)]
    (fn [req]
      (if-letk [[user-info] (some-> (get-token req) oauth2-introspect <!!)]
        (handler (assoc req :user-info user-info))
        {:status 401
         :body "Unauthorized"}))))
