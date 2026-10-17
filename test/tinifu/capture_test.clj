(ns tinifu.capture-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.capture :as cap]))

(deftest pw-record-command
  (is (= ["pw-record" "--rate" "16000" "--channels" "1" "--format" "s16"
          "--runtime" "30" "--" "/tmp/x.wav"]
         (cap/recorder-command :pw-record "/tmp/x.wav"))))

(deftest arecord-command
  (is (= ["arecord" "-f" "S16_LE" "-r" "16000" "-c" "1" "-t" "wav"
          "-d" "30" "/tmp/x.wav"]
         (cap/recorder-command :arecord "/tmp/x.wav"))))

(deftest record-uses-first-tool
  (let [ran (atom [])]
    (is (= :pw-record
           (cap/record! "/tmp/x.wav" {:runner (fn [cmd] (swap! ran conj cmd) {:exit 0})})))
    (is (= 1 (count @ran)))))

(deftest record-falls-back-to-arecord
  (let [ran (atom [])]
    (is (= :arecord
           (cap/record! "/tmp/x.wav"
                        {:runner (fn [cmd]
                                   (swap! ran conj cmd)
                                   {:exit (if (= (first cmd) "pw-record") 1 0)})})))
    (is (= 2 (count @ran)))))

(deftest record-throws-when-all-fail
  (is (nil? (cap/record! "/tmp/x.wav" {:runner (fn [_] {:exit 1})}))))
