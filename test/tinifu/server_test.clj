(ns tinifu.server-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.server :as server]))

(def handlers
  {:state-fn (fn [] {:state :idle :since 0})
   :transcribe-fn (fn [_] {:text "hi"})
   :ptt-start-fn (fn [] {})
   :ptt-end-fn (fn [] {:text "typed"})})

(deftest ptt-start-ok
  (is (= "{\"ok\":true}\n" (server/handle-request {:cmd :ptt-start} handlers))))

(deftest ptt-start-error
  (is (= "{\"error\":\"already recording\"}\n"
         (server/handle-request {:cmd :ptt-start}
                                (assoc handlers :ptt-start-fn (fn [] {:error "already recording"}))))))

(deftest ptt-end-returns-transcription
  (is (re-find #"\"text\":\"typed\"" (server/handle-request {:cmd :ptt-end} handlers))))

(deftest ptt-end-error
  (is (= "{\"error\":\"not recording\"}\n"
         (server/handle-request {:cmd :ptt-end}
                                (assoc handlers :ptt-end-fn (fn [] {:error "not recording"}))))))
