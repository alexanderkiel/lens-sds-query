(ns lens.handler-test
  (:require [clojure.test :refer :all]
            [lens.handler :refer :all]
            [om.next.server :as om]
            [datomic.api :as d])
  (:refer-clojure :exclude [read]))

(deftest validate-t
  (try
    ((om/parser {:read read}) nil '[(:query {:t -1})])
    (catch Exception e
      (is (= 400 (:status (ex-data e))))
      (is (= "Invalid T." (.getMessage e))))))

(deftest validate-study-oid
  (testing "Missing"
    (try
      ((om/parser {:read read}) nil '[(:query {:t 1})])
      (catch Exception e
        (is (= 400 (:status (ex-data e))))
        (is (= "Invalid or missing Study OID." (.getMessage e))))))
  (testing "Empty"
    (try
      ((om/parser {:read read}) nil '[(:query {:t 1 :study-oid ""})])
      (catch Exception e
        (is (= 400 (:status (ex-data e))))
        (is (= "Invalid or missing Study OID." (.getMessage e)))))))

(deftest validate-query
  (testing "Missing"
    (try
      ((om/parser {:read read}) nil '[(:query {:t 1 :study-oid "a"})])
      (catch Exception e
        (is (= 400 (:status (ex-data e))))
        (is (= "Invalid or missing Query: (not (map? nil))" (.getMessage e))))))
  (testing "Empty"
    (try
      ((om/parser {:read read}) nil '[(:query {:t 1 :study-oid "a" :query {}})])
      (catch Exception e
        (is (= 400 (:status (ex-data e))))
        (is (= "Invalid or missing Query: {:qualifier missing-required-key}"
               (.getMessage e)))))))

(deftest sync-t
  (d/create-database "datomic:mem://test")
  (try
    ((om/parser {:read read})
      {:conn (d/connect "datomic:mem://test")}
      '[(:query {:t 2000 :study-oid "a" :query {:qualifier [:and [:form "b"]]}})])
    (catch Exception e
      (is (= 422 (:status (ex-data e))))
      (is (= "Unable to sync on t: 2000" (.getMessage e))))))
