(ns tinifu.runner
  "Process runner over jolt.process: (fn [cmd-vector]) -> {:exit n}."
  (:require [jolt.process :as p]))

(defn run
  "Run a command vector, block, return {:exit n}."
  [cmd]
  (let [{:keys [exit]} (p/sh cmd)]
    {:exit exit}))
