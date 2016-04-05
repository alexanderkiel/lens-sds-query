(ns lens.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [schema.core :as s :refer [Int]]
            [cognitect.transit :as t]
            [lens.logging :refer [trace]])
  (:import [java.io ByteArrayOutputStream]))

(defn parse-long [s]
  (Long/parseLong s))

(def Ms
  "Duration in milliseconds."
  s/Num)

(s/defn duration :- Ms
  "Returns the duaration in milliseconds from a System/nanoTime start point."
  [start :- Int]
  (/ (double (- (System/nanoTime) start)) 1000000.0))

;; ---- Transit ---------------------------------------------------------------

(defn read-transit [in type]
  (try (t/read (t/reader in type)) (catch Exception e e)))

(defn write-transit [format write-opts o]
  (let [out (ByteArrayOutputStream.)]
    (t/write (t/writer out format write-opts) o)
    (io/input-stream (.toByteArray out))))

;; ---- Schema ----------------------------------------------------------------

(def NonBlankStr
  (s/constrained s/Str (complement str/blank?) 'non-blank?))

(def OID
  (s/named NonBlankStr "OID"))

(def NonNegInt
  (s/constrained s/Int (comp not neg?) 'non-neg?))

(def T
  (s/named NonNegInt "t"))

(def EId
  (s/named NonNegInt "eid"))
