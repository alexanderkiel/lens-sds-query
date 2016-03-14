(ns lens.middleware.auth-test
  (:require [clojure.test :refer :all]
            [lens.middleware.auth :refer :all]))

(deftest get-token-test
  (is (nil? (get-token {:headers {}})))
  (is (nil? (get-token {:headers {"authorization" nil}})))
  (is (nil? (get-token {:headers {"authorization" ""}})))
  (is (nil? (get-token {:headers {"authorization" "a"}})))
  (is (nil? (get-token {:headers {"authorization" "a b"}})))
  (is (= "a" (get-token {:headers {"authorization" "Bearer a"}}))))

(deftest warp-auth-test
  (is (= 401 (:status ((wrap-auth identity "uri") {:headers {}})))))
