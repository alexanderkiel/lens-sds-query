(ns user
  (:use plumbing.core)
  (:use criterium.core)
  (:require [clojure.core.async :refer [thread]]
            [clojure.pprint :refer [pprint pp]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [datomic.api :as d]
            [schema.core :as s :refer [Str]]
            [com.stuartsierra.component :as comp]
            [lens.system :refer [new-system]]
            [lens.query :as query]
            [environ.core :refer [env]]
            [lens.util :as u]
            [org.httpkit.client :as http]))

(s/set-fn-validation! true)

(def system nil)

(defn init []
  (when-not system (alter-var-root #'system (constantly (new-system env)))))

(defn start []
  (alter-var-root #'system comp/start))

(defn stop []
  (alter-var-root #'system comp/stop))

(defn startup []
  (init)
  (start)
  (println "Server running at port" (:port system)))

(defn reset []
  (stop)
  (refresh :after 'user/startup))

(defn connect []
  (:conn (:db-creator system)))

;; Init Development
(comment
  (startup)
  )

;; Reset after making changes
(comment
  (reset)
  )

;; Connection and Database in the REPL
(comment
  (def conn (connect))
  (def db (d/db conn))
  )

;; Init Remote Console
(comment
  (in-ns 'user)
  (init)
  )

(defn- write-transit [o]
  (u/write-transit :json {} o))

(defn- send-query [uri query]
  (http/post uri {:body (write-transit query) :as :stream}))

(comment
  (startup)
  (reset)

  (-> (send-query
        #_"http://localhost:5007/query"
        "http://192.168.99.100/query"
        [(list :query {:study-oid "S001"
                       :query {:qualifier
                               [:and
                                [:form "T00001"] [:form "D00074"]
                                [:study-event "A1_HAUPT01"]
                                ["Hochschulabschluss" :or
                                 [:item "T00001_F0050" [:= 1]] [:item "T00001_F0051" [:= 1]]]
                                [:item "D00074_F0004" [:> 25]]]}})])
      (deref)
      (update :body #(u/read-transit % :json))
      (select-keys [:status :body])
      (time))

  )

(comment
  (startup)
  (def db (d/db (connect)))
  (reset! query/cache (query/new-cache 512))

  ; Question: "Wieviel Teilnehmer in Soziodemographie der Adult-Hauptstudie?"
  ; 9465
  (->> {:qualifier [:and [:form "T00001"] [:study-event "A1_HAUPT01"]]}
       (query/query db "S001")
       (time))

  ; Question: "Wieviel Teilnehmer in Soziodemographie der Hauptstudie mit
  ;            Hochschulabschluss?"
  (->> {:qualifier
        [:and
         [:form "T00001"]
         [:study-event "A1_HAUPT01"]
         ["Hochschulabschluss" :or
          [:item "T00001_F0050" [:= 1]] [:item "T00001_F0051" [:= 1]]]]}
       (query/query db "S001")
       (time))

  ; Question: "Wieviel Teilnehmer in Soziodemographie der Hauptstudie mit
  ;            Hochschulabschluss und älter als 60 Jahre?"
  (->> {:qualifier
        [:and
         [:form "T00001"]
         [:study-event "A1_HAUPT01"]
         ["Hochschulabschluss" :or
          [:item "T00001_F0050" [:= 1]] [:item "T00001_F0051" [:= 1]]]
         [:item "age" [:> 60]]]}
       (query/query db "S001")
       (time))

  ; Question: "Wieviel Teilnehmer in Soziodemographie der Hauptstudie mit
  ;            Hochschulabschluss und einen BMI > 25?"
  (->> {:qualifier
        [:and
         [:form "T00001"] [:form "D00074"]
         [:study-event "A1_HAUPT01"]
         ["Hochschulabschluss" :or
          [:item "T00001_F0050" [:= 1]] [:item "T00001_F0051" [:= 1]]]
         [:item "D00074_F0004" [:> 25]]]}
       (query/query db "S001")
       (time))

  ; Question: "Wieviel Typ-2-Diabetiker (gemaess Eigenangabe) mit BMI > 25
  ;            und Albumin im Urin < 20mg/l und Microalbumine < 500 mg/l
  ;            und HbA1c 7 - 8,5 % ?"
  (->> {:qualifier
        [:and
         ["Forms" :and [:form "T00058"] [:form "D00074"] [:form "T00500"]
          [:form "T00439"] [:form "T00440"]]
         [:study-event "A1_HAUPT01"]
         ["Hochschulabschluss" :or
          [:item "T00001_F0050" [:= 1]] [:item "T00001_F0051" [:= 1]]]
         [:item "T00058_F0114" [:> 99]]                     ; Diabetiker
         [:item "D00074_F0004" [:> 25]]                     ; BMI
         [:item "T00500_F0008" [:< 20]]                     ; Albumin im Urin
         [:item "T00439_F0008" [:< 500]]                    ; Microalbumine
         [:item "T00440_F0008" '[7 8.5 <]]]

        ; "Weitere Ausschlusskriterien, um sicher zu gehen, dass die Messwerte
        ;  nicht von anderen Einflussfaktoren verzerrt wurden"
        :disqualifier
        [:not
         [:or
          [:item "T00058_F0008" [:= 1]]
          [:item "T00173_F0131" [:= 1]]
          [:item "T00173_F0231" [:= 1]]]]}
       (query/query db "S001")
       (time))

  )
