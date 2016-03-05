(ns lens.query-test
  (:require [clojure.test :refer :all]
            [lens.query :refer :all]
            [schema.experimental.generators :as g]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]))

(deftest equals-predicate-rules-test
  (are [value rules] (= rules (predicate-rules [:= value]))
    "a" '[[(item-value ?i)
           [?i :item/string-value ?v]
           [(= ?v "a")]]]
    "b" '[[(item-value ?i)
           [?i :item/string-value ?v]
           [(= ?v "b")]]]
    1 '[[(item-value ?i)
         [?i :item/integer-value ?v]
         [(= ?v 1)]]
        [(item-value ?i)
         [?i :item/float-value ?v]
         [(long ?v) ?lv]
         [(= ?lv 1)]]]
    2 '[[(item-value ?i)
         [?i :item/integer-value ?v]
         [(= ?v 2)]]
        [(item-value ?i)
         [?i :item/float-value ?v]
         [(long ?v) ?lv]
         [(= ?lv 2)]]]
    1.0 '[[(item-value ?i)
           [?i :item/float-value ?v]
           [(= ?v 1.0)]]]))

(deftest in-predicate-rules-test
  (are [values rules] (= rules (predicate-rules (apply vector :in values)))
    ["a"] '[[(item-value ?i)
             [?i :item/string-value ?v]
             [(#{"a"} ?v)]]]
    ["a" "b"] '[[(item-value ?i)
                 [?i :item/string-value ?v]
                 [(#{"a" "b"} ?v)]]]
    [1] '[[(item-value ?i)
           [?i :item/integer-value ?v]
           [(#{1} ?v)]]]
    [1 2] '[[(item-value ?i)
             [?i :item/integer-value ?v]
             [(#{1 2} ?v)]]]))

(deftest range-predicate-rules-test
  (are [op value rules] (= rules (predicate-rules [op value]))
    :< 1.1 '[[(item-value ?i)
              [?i :item/float-value ?v]
              [(< ?v 1.1)]]]))

(deftest interval-predicate-rules-test
  (are [interval rules] (= rules (predicate-rules interval))
    [1 2] '[[(item-value ?i)
             [?i :item/integer-value ?v]
             [(>= ?v 1)]
             [(<= ?v 2)]]
            [(item-value ?i)
             [?i :item/float-value ?v]
             [(>= ?v 1.0)]
             [(<= ?v 2.0)]]]))

(defspec predicate-rules-check 1000
  (prop/for-all [p (g/generator Predicate)]
    (nil? (s/check [PredicateRule] (predicate-rules p)))))

(deftest query-study-event-atom
  (testing "over empty database"
    (is #{} (query-atom* [] [:study-event "SE1"])))
  (testing "with one study event"
    (let [db [[1 :study-event/oid "SE1"]]]
      (is #{1} (query-atom* db [:study-event "SE1"]))))
  (testing "with two study events"
    (let [db [[1 :study-event/oid "SE1"]
              [2 :study-event/oid "SE1"]]]
      (is #{1 2} (query-atom* db [:study-event "SE1"]))))
  (testing "with other study-event"
    (let [db [[1 :study-event/oid "SE1"]
              [2 :study-event/oid "SE2"]]]
      (is #{1} (query-atom* db [:study-event "SE1"])))))

(deftest query-form-atom
  (testing "over empty database"
    (is #{} (query-atom* [] [:form "F1"])))
  (testing "with one study event"
    (let [db [[1 :form/oid "F1"]
              [2 :study-event/forms 1]]]
      (is #{2} (query-atom* db [:form "F1"]))))
  (testing "with two study events"
    (let [db [[1 :form/oid "F1"]
              [2 :study-event/forms 1]
              [3 :study-event/forms 1]]]
      (is #{2 3} (query-atom* db [:form "F1"]))))
  (testing "with other form"
    (let [db [[1 :form/oid "F1"]
              [2 :form/oid "F2"]
              [2 :study-event/forms 1]
              [3 :study-event/forms 2]]]
      (is #{2} (query-atom* db [:form "F1"])))))

(deftest query-atom-item
  (testing "over empty database"
    (is #{} (query-atom* [] [:item "I1"])))
  (testing "with one study event"
    (let [db [[1 :item/oid "I1"]
              [2 :item-group/items 1]
              [3 :form/item-groups 2]
              [4 :study-event/forms 3]]]
      (is #{4} (query-atom* db [:item "I1"]))))
  (testing "with one study event and string equals predicate"
    (let [db [[1 :item/oid "I1"]
              [1 :item/string-value "a"]
              [2 :item-group/items 1]
              [3 :form/item-groups 2]
              [4 :study-event/forms 3]]]
      (is #{4} (query-atom* db [:item "I1" [:= "a"]])))))

(defspec query-atom-check 1000
  (prop/for-all [a (g/generator Atom)]
    (= #{} (query-atom* [] a))))
