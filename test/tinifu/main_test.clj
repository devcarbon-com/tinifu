(ns tinifu.main-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.main :as main]))

(deftest commands-listed
  (is (= #{"transcribe" "daemon" "activate" "ptt"} (set (map :name main/commands)))))

(deftest usage-mentions-commands
  (let [u (with-out-str (main/usage))]
    (doseq [c ["transcribe" "daemon" "activate" "ptt"]]
      (is (.contains u c)))))

(deftest unknown-command-runs-usage-and-errors
  (let [out (with-out-str (is (= 2 (main/dispatch "bogus" ["arg"]))))]
    (is (.contains out "usage"))))
