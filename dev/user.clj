(ns user
  (:use plumbing.core)
  (:use criterium.core)
  (:require [clojure.core.async :refer [thread]]
            [clojure.core.reducers :as r]
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
  (:conn (:database system)))

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
  (startup)
  )

(defn- write-transit [o]
  (u/write-transit :json {} o))

(defn- send-query [uri query]
  (http/post uri {:body (write-transit query) :as :stream}))

(comment
  (startup)
  (reset)

  (-> (send-query
        "http://localhost:5007/query"
        ;"http://192.168.99.100/query"
        '[(:query {:study-oid "S001" :query {:qualifier [:and [:form "T00001"] [:study-event "A1_HAUPT01"]]}})])
      (deref)
      (update :body #(u/read-transit % :json))
      (select-keys [:status :body])
      (time))

  (println (slurp (write-transit '[(:query {:qualifier [:and [:form "T00001"] [:study-event "A1_HAUPT01"]]})])))

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
         [:item "D00153_AGE" [:> 60]]]}
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

(comment
  (startup)
  (pst)
  (def db (d/db (connect)))
  (def data-1
    (d/q '[:find ?s-oid ?f-oid (count ?f)
           :where
           [?s :study/oid "T00449"]
           [?s :study/subjects ?sub]
           [?sub :subject/study-events ?se]
           [?se :study-event/forms ?f]
           [?f :form/oid ?f-oid]] db))

  (def data-2
    (reduce (fn [r [study-oid form-oid count]]
              (if form-oid
                (assoc-in r [study-oid form-oid] count)
                r)) {} data-1))

  (def data-3 (read-string (slurp "form-stat-data1.edn")))

  (pprint (take 2 (clojure.data/diff data-2 data-3)))

  )

(defn count-datoms [coll]
  (reduce + (r/map (constantly 1) coll)))

;; Counts
(comment
  (startup)
  (def db (d/db (connect)))
  (d/basis-t db)
  (count-datoms (d/datoms db :aevt :subject/id))
  (count-datoms (d/datoms db :aevt :study-event/id))
  (count-datoms (d/datoms db :aevt :form/id))
  (count-datoms (d/datoms db :aevt :item-group/id))
  (count-datoms (d/datoms db :aevt :item/id))

  (count-datoms (d/datoms db :avet :event/name :form/removed))

  )

(comment
  (d/basis-t db)
  (d/pull db '[*] (d/t->tx (d/basis-t db)))
  (->> (d/tx-range (d/log (connect)) 87811987 nil)
       (drop 1)
       (map :t)
       (map d/t->tx)
       (map #(d/pull db '[* {:event/_tx [:event/name]}] %))
       (filter #(#{:form/created} (-> % :event/_tx first :event/name)))
       (count))
  )

