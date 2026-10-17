(ns tinifu.runner
  "Process runner over jolt.process: (fn [cmd-vector]) -> {:exit n}."
  (:require [jolt.process :as p]))

(defn run
  "Run a command vector, block, return {:exit n}. A command that cannot be
  executed at all (binary missing) counts as a non-zero exit so callers'
  fallback chains work."
  [cmd]
  (try
    (let [{:keys [exit]} (p/sh cmd)]
      {:exit exit})
    (catch Exception _ {:exit 127})))
