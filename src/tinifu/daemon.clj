(ns tinifu.daemon
  "Daemon entry: load the model once, serve the socket until stopped, and run
  a tray icon (SNI via the C shim) that reflects state and toggles a voice
  session on Activate.

  jolt -A:time -m tinifu.daemon [--no-tray]"
  (:require [tinifu.audio :as audio]
            [tinifu.server :as server]
            [tinifu.state :as st]
            [tinifu.tray :as tray]
            [tinifu.whistle :as whistle]))

(defn- tray-loop!
  "SNI loop until state goes idle after a stop request. activate → run one
  voice session (capture + transcribe + inject) via tinifu.session."
  [state stop?]
  (require 'tinifu.session)
  (let [session! (ns-resolve 'tinifu.session 'cli-session!)]
    (tray/set-state! {:status "Passive" :icon "audio-headphones" :tooltip "tinifu: idle"})
    (loop []
      (when-not @stop?
        (if-some [ev (tray/poll! 300)]
          (case ev
            :activate (do
                        (tray/set-state! {:status "NeedsAttention" :icon "audio-input-microphone"
                                          :tooltip "tinifu: listening"})
                        ;; a session failure (no recorder, no injector) must not
                        ;; kill the tray thread
                        (let [{:keys [error text]}
                              (try (session! {})
                                   (catch Exception e {:error (str (.getMessage e))}))]
                          (tray/set-state!
                           (if error
                             {:status "NeedsAttention" :icon "dialog-error"
                              :tooltip (str "tinifu: " error)}
                             {:status "Passive" :icon "audio-headphones"
                              :tooltip (str "tinifu: " text)}))))
            :secondary (reset! stop? true))
          (recur)))))
  (tray/close!))

(defn -main [& args]
  (let [state (atom {:state :idle})
        no-tray? (some #{"--no-tray"} args)
        stop? (atom false)]
    (when-not no-tray?
      (tray/init! (str "org.kde.StatusNotifier.tinifu-" (.pid (java.lang.ProcessHandle/current))))
      (doto (Thread. #(tray-loop! state stop?)) .start))
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
      :stop-fn (fn [] (reset! stop? true) (reset! state {:state :idle}))})))
