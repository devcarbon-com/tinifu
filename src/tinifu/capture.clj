(ns tinifu.capture
  "Microphone capture by shelling out (injected runner, no numpy): default
  recorder is pw-record, arecord fallback. Command construction is pure and
  tested; the runner is a fn of a command vector that blocks until exit.")

(def recorder-commands
  {:pw-record ["pw-record" "--rate" "16000" "--channels" "1" "--format" "s16" "--" "OUT"]
   :arecord   ["arecord" "-f" "S16_LE" "-r" "16000" "-c" "1" "-t" "wav"
               "-d" "30" "OUT"]})

(defn recorder-command
  "Command vector for tool, with OUT substituted by the output path."
  [tool out-path]
  (mapv #(if (= % "OUT") out-path %) (get recorder-commands tool)))

(defn- wrapped-command
  "pw-record has no duration flag; cap it with coreutils timeout. A 124 exit
  (killed by the timeout) means the recording completed normally."
  [cmd max-seconds]
  (if max-seconds
    (into ["timeout" (str max-seconds)] cmd)
    cmd))

(defn record!
  "Record mono 16k PCM16 WAV to out-path with the first available tool.
  runner: (fn [cmd-vector]) -> {:exit n}. :max-seconds caps the recording
  with coreutils timeout (124 exit treated as success). Returns the tool
  used; throws when the command exits non-zero or no tool is available."
  [out-path {:keys [runner available max-seconds]}]
  (let [tools (if available available [:pw-record :arecord])]
    (loop [[tool & more] tools]
      (when tool
        (let [cmd (wrapped-command (recorder-command tool out-path) max-seconds)
              {:keys [exit]} (runner cmd)]
          (if (or (zero? exit) (= exit 124))
            tool
            (recur more)))))))
