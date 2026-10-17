(ns tinifu.daemon
  "Daemon entry: load the model once, serve the socket until stopped.

  jolt -A:time -m tinifu.daemon"
  (:require [tinifu.audio :as audio]
            [tinifu.server :as server]
            [tinifu.whistle :as whistle]))

(defn -main [& _]
  (let [state (atom {:state :idle})]
    (println "tinifu daemon listening on" (server/socket-path))
    (flush)
    (server/serve!
     {:state state}
     {:state-fn (fn [] @state)
      :transcribe-fn (fn [path]
                       (swap! state assoc :state :transcribing)
                       (try
                         (let [r (whistle/transcribe-bytes (audio/to-f32 (whistle/slurp path)) {})]
                           (swap! state assoc :state :idle)
                           r)
                         (catch Exception e
                           (swap! state assoc :state :idle)
                           {:error (str (.getMessage e))})))
      :stop-fn (fn [] (reset! state {:state :idle}))})))
