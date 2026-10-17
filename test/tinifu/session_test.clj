(ns tinifu.session-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.session :as sess]))

(deftest happy-path
  (let [calls (atom [])
        result (sess/run-session!
                {:transcribe-fn (fn [p] (swap! calls conj p) {:text "hello world"})
                 :capture-opts {:runner (fn [_] {:exit 0})}
                 :inject-opts {:runner (fn [_] {:exit 0})}
                 :tmp-dir "/tmp"})]
    (is (= "hello world" (:text result)))
    (is (= :wtype (:injector result)))))

(deftest recorder-failure
  (is (= {:error "no recorder available"}
         (sess/run-session! {:transcribe-fn (fn [_] {:text "x"})
                             :capture-opts {:runner (fn [_] {:exit 1})}
                             :inject-opts {:runner (fn [_] {:exit 0})}}))))

(deftest transcription-failure
  (is (= {:error "boom"}
         (sess/run-session! {:transcribe-fn (fn [_] {:error "boom"})
                             :capture-opts {:runner (fn [_] {:exit 0})}
                             :inject-opts {:runner (fn [_] {:exit 0})}}))))

(deftest no-injector
  (let [r (sess/run-session! {:transcribe-fn (fn [_] {:text "x"})
                              :capture-opts {:runner (fn [_] {:exit 0})}
                              :inject-opts {:runner (fn [_] {:exit 1})}})]
    (is (= "no injector available" (:error r)))))
