(ns jepsen.hazelcast_test
  (:require [clojure.test :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.hazelcast :as hz])
  )

(deftest hz-test
  (is (:valid? (:results (jepsen/run! (hz/hz-test "3.7-EA"))))))
