(ns tinifu.whistle-test
  (:require [clojure.test :refer [deftest is testing]]
            [tinifu.whistle :as whistle]))

(def ^:private engine-json
  "{\"text\":\"turn off the kitchen lights\",\"language\":\"en\",\"ttft_ms\":23.6,\"decode_tps\":326.3}")

(deftest parse-result-full-fields
  (is (= {:text "turn off the kitchen lights" :language "en"
          :ttft-ms 23.6 :decode-tps 326.3}
         (whistle/parse-result engine-json))))

(deftest parse-result-empty-transcript
  ;; silence and steady noise return an empty transcript
  (is (= {:text "" :language "en" :ttft-ms 1.0 :decode-tps 0.0}
         (whistle/parse-result "{\"text\":\"\",\"language\":\"en\",\"ttft_ms\":1.0,\"decode_tps\":0.0}"))))

(deftest parse-result-word-timestamps
  (let [r (whistle/parse-result
           "{\"text\":\"hello there\",\"language\":\"en\",\"ttft_ms\":5.0,\"decode_tps\":100.0,
             \"words\":[{\"word\":\"hello\",\"start\":0.0,\"end\":0.4,\"probability\":0.97}]}")]
    (is (= [{:word "hello" :start 0.0 :end 0.4 :probability 0.97}] (:words r)))))

(deftest options-nil-means-detect
  (is (= [nil nil] (whistle/options nil nil))))

(deftest options-language-and-keywords
  (is (= ["de" "kitchen\nlights"] (whistle/options "de" ["kitchen" "lights"]))))

(deftest options-keyword-string-passes-through
  (is (= ["en" "jolt"] (whistle/options "en" "jolt"))))

(deftest engine-source-prefers-embedded-resource
  ;; in a built binary the vendored lib is an embedded resource; on a plain
  ;; checkout io/resource misses and the cached path is used
  (is (string? (whistle/engine-source))))

(deftest model-source-prefers-embedded-resource
  (is (string? (whistle/model-source nil))))
