(ns tinifu.main
  "Single-binary entry point: tinifu <command> [args...]. Each command's
  options and -main live in its own namespace; this just dispatches."
  (:require [tinifu.activate]
            [tinifu.daemon]
            [tinifu.ptt]
            [tinifu.transcribe])
  (:gen-class))

(def commands
  [{:name "transcribe" :ns 'tinifu.transcribe
    :desc "transcribe a WAV file: transcribe clip.wav [--lang de] [--keywords a,b] [--timestamps]"}
   {:name "daemon" :ns 'tinifu.daemon
    :desc "start the background daemon: daemon [--no-tray]"}
   {:name "activate" :ns 'tinifu.activate
    :desc "run one voice session (record → transcribe → type): activate"}
   {:name "ptt" :ns 'tinifu.ptt
    :desc "push-to-talk: ptt start | ptt end"}])

(defn usage
  "Print usage to stdout."
  []
  (println "usage: tinifu <command> [args...]")
  (doseq [{:keys [name desc]} commands]
    (println (str "  " name "  " desc))))

(defn dispatch
  "Run a command, return its process exit code."
  [cmd args]
  (if-some [{:keys [ns]} (some #(when (= (:name %) cmd) %) commands)]
    (try (apply (ns-resolve ns '-main) args) 0
         (catch Exception e
           (println "tinifu:" (.getMessage e)) 1))
    (do (println "tinifu: unknown command" (pr-str cmd))
        (usage)
        2)))

(defn -main [& args]
  (if (seq args)
    (System/exit (dispatch (first args) (vec (rest args))))
    (do (usage) (System/exit 2))))
