(ns lens.query
  "Run queries agains the database with run-query."
  (:require [clojure.core.cache :as cache]
            [clojure.set :as set]
            [datomic.api :as d]
            [lens.util :refer [EId T]]
            [schema.core :as s :refer [Any Keyword]]))

;; ---- Schemas ---------------------------------------------------------------

(defn- first-is [check]
  (let [pred (if (or (fn? check) (set? check)) check (partial = check))]
    (fn [to-check] (-> to-check first pred))))

; RangePredicate := {':<' | ':<=' | ':>' | ':>='} s/Num
(def RangePredicate
  (s/conditional
    (first-is #{:< :<= :> :>=})
    [(s/one (s/enum :< :<= :> :>=) "comparator") (s/one s/Num "value")]))

; Interval := '[' {ε | '>'} s/Num s/Num {ε | '<'} ']'
(def Interval
  (s/conditional
    (first-is '>) [(s/one (s/eq '>) "lower-boundary-mod")
                   (s/one s/Num "lower-boundary")
                   (s/one s/Num "upper-boundary")
                   (s/optional (s/eq '<) "upper-boundary-mod")]

    (first-is number?) [(s/one s/Num "lower-boundary")
                        (s/one s/Num "upper-boundary")
                        (s/optional (s/eq '<) "upper-boundary-mod")]))

(def Value
  "A value of an item."
  (s/cond-pre s/Str s/Num))

; EqualPredicate := '(' ':=' Value ')'
(def EqualPredicate
  (s/conditional
    (first-is :=)
    [(s/one (s/eq :=) "equals") (s/one Value "value")]))

; InPredicate := {'(' ':in' s/Str+ ')' | '(' ':in' s/Int+ ')'}
(def InPredicate
  (s/conditional
    (fn [[op first]] (and (= :in op) (string? first)))
    [(s/one (s/eq :in) "in")
     (s/one s/Str "first")
     s/Str]
    (fn [[op first]] (and (= :in op) (integer? first)))
    [(s/one (s/eq :in) "in")
     (s/one s/Int "first")
     s/Int]))

; Predicate :=  RangePredicate | Interval | EqualPredicate | InPredicate
(def Predicate
  (s/cond-pre
    RangePredicate
    Interval
    EqualPredicate
    InPredicate))

; Item := '[' ':item' s/Str {ε | Predicate} ']'
(def Item
  [(s/one (s/eq :item) "type")
   (s/one s/Str "identifier")
   (s/optional Predicate "predicate")])

; Form := '[' ':form' s/Str ']'
(def Form
  [(s/one (s/eq :form) "type")
   (s/one s/Str "identifier")])

; StudyEvent := '[' ':study-event' s/Str ']'
(def StudyEvent
  [(s/one (s/eq :study-event) "type")
   (s/one s/Str "identifier")])

; Atom := StudyEvent | Form | Item
(def Atom
  (s/conditional
    (first-is :study-event) StudyEvent
    (first-is :form) Form
    (first-is :item) Item))

; Operation := ':and' | ':or'
(def Operation
  (s/enum :and :or))

; s/Str
(def Name
  s/Str)

; Expression := Atom
;             | '(' Name Operation Expression+ ')'
;             | '(' ':not' Expression ')'
(def Expression
  (s/conditional
    (first-is :not)
    [(s/one (s/eq :not) "negation")
     (s/one (s/recursive #'Expression) "expression")]

    (first-is string?)
    [(s/one Name "name")
     (s/one Operation "operation")
     (s/one (s/recursive #'Expression) "first expression")
     (s/recursive #'Expression)]

    :else Atom))

; Qualifiers := '(' ':and' Expression+ ')'
(def Qualifiers
  [(s/one (s/eq :and) "conj")
   (s/one Expression "first")
   Expression])

; Disqualifiers := '(' ':not' '(' ':or' Expression+ ')' ')'
(def Disqualifiers
  [(s/one (s/eq :not) "negation")
   (s/one [(s/one (s/eq :or) "disj") (s/one Expression "first") Expression]
          "disqualifier")])

(def Query
  {:qualifier Qualifiers
   (s/optional-key :disqualifier) Disqualifiers})

;; ---- Private ---------------------------------------------------------------

(defmulti query-atom*
          "Returns a seq of study-event eids which match the atom."
          (fn [_ [type]] type))

(defmethod query-atom* :study-event
  [db [_ study-event-oid]]
  (set (d/q '[:find [?se ...]
              :in $ ?se-oid
              :where
              [?se :study-event/oid ?se-oid]]
            db study-event-oid)))

(defmethod query-atom* :form
  [db [_ form-oid]]
  (set (d/q '[:find [?se ...]
              :in $ ?f-oid
              :where
              [?f :form/oid ?f-oid]
              [?se :study-event/forms ?f]]
            db form-oid)))

(def PredicateRule
  [(s/one (s/eq '(item-value ?i)) "head")
   (s/one
     [(s/one (s/eq '?i) "?i")
      (s/one Keyword "attr")
      (s/one (s/eq '?v) "?v")]
     "attr-clause")
   [Any]])

(defmulti predicate-rules
          "Takes a predicate like [:= 10] and returns an (item-value ?i) rule."
          first)

(defn- numeric-predicate-rules
  "Returns a rule which tests the items value in all numeric types."
  [op value]
  [['(item-value ?i)
    '[?i :item/integer-value ?v]
    [`(~op ~'?v ~value)]]
   ['(item-value ?i)
    '[?i :item/float-value ?v]
    [`(~op ~'?v ~(double value))]]])

(defn- float-predicate-rules
  "Returns a rule which tests the items value in float type."
  [op value]
  [['(item-value ?i)
    '[?i :item/float-value ?v]
    [`(~op ~'?v ~(double value))]]])

(defmethod predicate-rules :=
  [[_ value]]
  (cond
    (string? value)
    [['(item-value ?i)
      '[?i :item/string-value ?v]
      [`(~'= ~'?v ~value)]]]
    (integer? value)
    [['(item-value ?i)
      '[?i :item/integer-value ?v]
      [`(~'= ~'?v ~value)]]
     ['(item-value ?i)
      '[?i :item/float-value ?v]
      '[(long ?v) ?lv]
      [`(~'= ~'?lv ~value)]]]
    :else
    (float-predicate-rules '= value)))

(defmethod predicate-rules :>
  [[_ value]]
  (if (integer? value)
    (numeric-predicate-rules '> value)
    (float-predicate-rules '> value)))

(defmethod predicate-rules :<
  [[_ value]]
  (if (integer? value)
    (numeric-predicate-rules '< value)
    (float-predicate-rules '< value)))

(defmethod predicate-rules :>=
  [[_ value]]
  (if (integer? value)
    (numeric-predicate-rules '>= value)
    (float-predicate-rules '>= value)))

(defmethod predicate-rules :<=
  [[_ value]]
  (if (integer? value)
    (numeric-predicate-rules '<= value)
    (float-predicate-rules '<= value)))

(defmethod predicate-rules :in
  [[_ & values]]
  (cond
    (every? string? values)
    [['(item-value ?i)
      '[?i :item/string-value ?v]
      [`(~(set values) ~'?v)]]]
    (every? integer? values)
    [['(item-value ?i)
      '[?i :item/integer-value ?v]
      [`(~(set values) ~'?v)]]]))

(defn- numeric-interval-rules [lb-op lb ub ub-op]
  [['(item-value ?i)
    '[?i :item/integer-value ?v]
    [`(~lb-op ~'?v ~lb)]
    [`(~ub-op ~'?v ~ub)]]
   ['(item-value ?i)
    '[?i :item/float-value ?v]
    [`(~lb-op ~'?v ~(double lb))]
    [`(~ub-op ~'?v ~(double ub))]]])

(defn- float-interval-rules [lb-op lb ub ub-op]
  [['(item-value ?i)
    '[?i :item/float-value ?v]
    [`(~lb-op ~'?v ~(double lb))]
    [`(~ub-op ~'?v ~(double ub))]]])

(defmethod predicate-rules :default
  [[lb :as interval]]
  (if (= '> lb)
    (let [[_ lb ub ub-op] interval]
      (if (and (integer? lb) (integer? ub))
        (numeric-interval-rules '> lb ub (or ub-op '<=))
        (float-interval-rules '> lb ub (or ub-op '<=))))
    (let [[lb ub ub-op] interval]
      (if (and (integer? lb) (integer? ub))
        (numeric-interval-rules '>= lb ub (or ub-op '<=))
        (float-interval-rules '>= lb ub (or ub-op '<=))))))

(defmethod query-atom* :item
  [db [_ item-oid predicate]]
  (if predicate
    (set (d/q '[:find [?se ...]
                :in $ % ?i-oid
                :where
                [?i :item/oid ?i-oid]
                (item-value ?i)
                [?ig :item-group/items ?i]
                [?f :form/item-groups ?ig]
                [?se :study-event/forms ?f]]
              db (predicate-rules predicate) item-oid))
    (set (d/q '[:find [?se ...]
                :in $ ?i-oid
                :where
                [?i :item/oid ?i-oid]
                [?ig :item-group/items ?i]
                [?f :form/item-groups ?ig]
                [?se :study-event/forms ?f]]
              db item-oid))))

(defn new-cache [threshold]
  (cache/lru-cache-factory {} :threshold threshold))

(def cache (atom (new-cache 512)))

(defn t [db]
  (or (d/as-of-t db) (d/basis-t db)))

(s/defn query-atom [db atom :- Atom]
  (let [key [(t db) atom]
        wrapper-fn #(%1 (second %2))
        value-fn #(query-atom* db %)]
    (get (swap! cache #(cache/through wrapper-fn value-fn % key)) key)))

(defn all-study-events [db]
  (into #{} (map :e) (d/datoms db :aevt :study-event/id)))

(declare query-expression)

(defn- negate
  "Returns the set difference between all study events and the study events the
  expression matches."
  [db expression]
  (set/difference (all-study-events db)
                  (query-expression db expression)))

(defn- combine [db op expressions]
  (let [op (case op :and set/intersection :or set/union)]
    (apply op (pmap #(query-expression db %) expressions))))

(s/defn query-expression [db [first :as expression] :- Expression]
  (cond
    (= :not first)
    (negate db (second expression))

    (string? first)
    (let [[_ op & expressions] expression]
      (combine db op expressions))

    :else
    (query-atom db expression)))

(s/defn run-query :- #{EId}
  "Runs the given query against db and returns a set of study-event eids."
  [db query :- Query]
  (let [qualifier (combine db :and (rest (:qualifier query)))
        disqualifier (combine db :or (rest (rest (:disqualifier query))))]
    (set/difference qualifier disqualifier)))
