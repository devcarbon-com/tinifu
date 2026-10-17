(ns tinifu.ptt
  "Push-to-talk entry point for compositor keybindings.

  jolt -A:time -m tinifu.ptt start   # on key press
  jolt -A:time -m tinifu.ptt end     # on key release

  The daemon records while the key is held; on release it transcribes and
  types the text into the focused window."
  (:require [tinifu.client :as client])
  (:gen-class))

(defn- sock-path []
  (or (System/getenv "TINIFU_SOCK")
      (str (or (System/getenv "XDG_RUNTIME_DIR") "/tmp") "/tinifu.sock")))

(defn -main [& [action]]
  (let [verb (case action
               "start" "ptt-start"
               "end" "ptt-end"
               (do (println "usage: tinifu.ptt [start|end]") (System/exit 2)))
        r (client/request (sock-path) verb)]
    (if (and r (string? r) (.contains ^String r "\"error\""))
      (do (println "tinifu ptt:" r) (System/exit 1))
      (when action
        (println "tinifu ptt:" r)))))
