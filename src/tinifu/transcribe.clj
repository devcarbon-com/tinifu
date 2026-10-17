(ns tinifu.transcribe
  "CLI entry: transcribe a 16 kHz (or resample-free) WAV file with Whistle.

  tinifu.transcribe <clip.wav> [--lang de] [--keywords a,b] [--timestamps]
  Prints the transcript text; --timestamps adds one word per line with times."
  (:require [clojure.string :as str]
            [tinifu.audio :as audio]
            [tinifu.whistle :as whistle]))

(defn- parse-args
  "Arg vector to {:wav path :language :keywords :word-timestamps}, or nil when
  the usage is wrong."
  [args]
  (loop [args args m nil]
    (if (seq args)
      (let [a (first args)]
        (cond
          (= "--lang" a) (recur (nnext args) (assoc m :language (second args)))
          (= "--keywords" a) (recur (nnext args)
                                    (assoc m :keywords (str/split (second args) #",")))
          (= "--timestamps" a) (recur (next args) (assoc m :word-timestamps true))
          (and (nil? m) (not (str/starts-with? a "--"))) (recur (next args) (assoc m :wav a))
          :else nil))
      (when (:wav m) m))))

(defn- print-result
  [{:keys [text words language ttft-ms decode-tps]} word-timestamps]
  (println text)
  (when word-timestamps
    (doseq [{:keys [word start end]} words]
      (println (format "%.2f-%.2f  %s" start end word)))))

(defn -main [& args]
  (if-some [{:keys [wav] :as opts} (parse-args args)]
    (let [wav-bytes (whistle/slurp wav)
          f32 (audio/to-f32 wav-bytes)
          result (whistle/transcribe-bytes f32 opts)]
      (print-result result (:word-timestamps opts)))
    (do (println "usage: tinifu.transcribe <clip.wav> [--lang de] [--keywords a,b] [--timestamps]")
        (System/exit 1))))
