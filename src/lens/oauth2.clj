(ns lens.oauth2
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [schema.core :as s :refer [Str]]
            [org.httpkit.client :refer [post]]
            [lens.logging :refer [debug trace]]))

(def UserInfo
  {:username Str})

(defn- deliver-resp! [ch resp]
  (when (= 200 (:status resp))
    (let [user-info (json/read-str (:body resp) :key-fn keyword)]
      (when (:active user-info)
        (async/put! ch {:user-info
                        (-> (dissoc user-info :active)
                            (assoc :sub (:username user-info)))})))))

(s/defn introspect
  "Introspects the token. Returns a channel conveying a map with :user-info or
  nil if the token is not active or there was another problem."
  [token-introspection-uri token]
  (let [ch (async/chan)]
    (debug {:action :introspect :token token})
    (post token-introspection-uri
          {:headers {"accept" "application/json"}
           :form-params {:token token}}
          (fn [resp]
            (trace {:action :introspect :token token :response resp})
            (deliver-resp! ch resp)
            (async/close! ch)))
    ch))
