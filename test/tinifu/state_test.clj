(ns tinifu.state-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.state :as st]))

(deftest full-loop
  (let [s1 (st/transition (st/initial) :start-recording)
        s2 (st/transition s1 :stop-recording)
        s3 (st/transition s2 :done)]
    (is (= :listening (:state s1)))
    (is (= :transcribing (:state s2)))
    (is (= :idle (:state s3)))))

(deftest start-only-from-idle
  (is (= :listening (:state (st/transition (st/initial) :start-recording))))
  ;; start-recording is ignored unless idle
  (is (= :transcribing (:state (st/transition {:state :transcribing} :start-recording)))))

(deftest cancel-resets
  (is (= :idle (:state (st/transition {:state :listening} :cancel)))))

(deftest error-resets
  (is (= :idle (:state (st/transition {:state :transcribing} :error)))))
