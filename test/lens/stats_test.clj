(ns lens.stats-test
  (:require [clojure.test :refer :all]
            [lens.stats :refer :all]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once validate-schemas)

(deftest update-form-count-test
  (are [count old new] (= (update-form-count old ["S001" "T00001" count]) new)
    1 {"S001" {}} {"S001" {"T00001" 1}}
    1 {"S001" {"T00001" 0}} {"S001" {"T00001" 1}}
    1 {"S001" {"T00001" 1}} {"S001" {"T00001" 2}}
    2 {"S001" {"T00001" 1}} {"S001" {"T00001" 3}}))
