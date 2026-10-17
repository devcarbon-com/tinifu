(ns tinifu.activate
  "Compositor keybinding entry point: runs one voice session end to end.

  jolt -A:time -m tinifu.activate          # record until silence, type text
  TINIFU_SOCK=/path tinifu.activate        # talk to a specific daemon"
  (:require [tinifu.session :as session])
  (:gen-class))

(defn -main [& _]
  (let [{:keys [text error injector]} (session/cli-session! {})]
    (cond
      error (do (println "tinifu:" error) (System/exit 1))
      :else (println "tinifu:" (pr-str text) "via" (name injector)))))
