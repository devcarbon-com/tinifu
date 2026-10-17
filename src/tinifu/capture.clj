(ns tinifu.capture
  "Microphone capture by shelling out (injected runner, no numpy): default
  recorder is pw-record, arecord fallback. Command construction is pure and
  tested; the runner is a fn of a command vector that blocks until exit.")

(def recorder-commands
  {:pw-record ["pw-record" "--rate" "16000" "--channels" "1" "--format" "s16"
               "--runtime" "30" "--" "OUT"]
   :arecord   ["arecord" "-f" "S16_LE" "-r" "16000" "-c" "1" "-t" "wav"
               "-d" "30" "OUT"]})

(defn recorder-command
  "Command vector for tool, with OUT substituted by the output path."
  [tool out-path]
  (mapv #(if (= % "OUT") out-path %) (get recorder-commands tool)))

(defn record!
  "Record mono 16k PCM16 WAV to out-path with the first available tool.
  runner: (fn [cmd-vector]) -> {:exit n}. Returns the tool used; throws when
  the command exits non-zero or no tool is available."
  [out-path {:keys [runner available]}]
  (let [tools (if available available [:pw-record :arecord])]
    (loop [[tool & more] tools]
      (when tool
        (let [cmd (recorder-command tool out-path)
              {:keys [exit]} (runner cmd)]
          (if (zero? exit)
            tool
            (recur more)))))))
