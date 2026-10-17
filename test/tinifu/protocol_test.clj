(ns tinifu.protocol-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.protocol :as proto]))

(deftest parse-transcribe
  (is (= {:cmd :transcribe :path "/tmp/clip.wav"}
         (proto/parse-request "transcribe /tmp/clip.wav")))
  (is (= {:cmd :transcribe :path "/tmp/clip.wav"}
         (proto/parse-request "  transcribe /tmp/clip.wav\n"))))

(deftest parse-transcribe-needs-path
  (is (:error (proto/parse-request "transcribe"))))

(deftest parse-status-and-stop
  (is (= {:cmd :status} (proto/parse-request "status")))
  (is (= {:cmd :stop} (proto/parse-request "stop"))))

(deftest parse-unknown-is-error
  (is (:error (proto/parse-request "bogus")))
  (is (:error (proto/parse-request ""))))

(deftest status-line-format
  (is (= "{\"state\":\"idle\",\"since\":0}\n" (proto/status->line {:state :idle}))))
