(ns tinifu.inject-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.inject :as inj]))

(deftest commands
  (is (= ["wtype" "--" "hi there"] (inj/inject-command :wtype "hi there")))
  (is (= ["ydotool" "type" "hi"] (inj/inject-command :ydotool "hi"))))

(deftest inject-uses-wtype
  (is (= :wtype (inj/inject! "x" {:runner (fn [_] {:exit 0})}))))

(deftest inject-falls-back-to-ydotool
  (is (= :ydotool
         (inj/inject! "x" {:runner (fn [cmd]
                                     {:exit (if (= (first cmd) "wtype") 1 0)})}))))

(deftest inject-returns-nil-when-all-fail
  (is (nil? (inj/inject! "x" {:runner (fn [_] {:exit 1})}))))
