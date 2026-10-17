(ns tinifu.daemon
  "Daemon entry: load the model once, serve the socket until stopped, and run
  a tray icon (SNI via the C shim) that reflects state and toggles a voice
  session on Activate.

  jolt -A:time -m tinifu.daemon [--no-tray]"
  (:require [tinifu.audio :as audio]
            [tinifu.capture :as cap]
            [tinifu.inject :as inj]
            [tinifu.runner :as runner]
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
    (tray/set-state! {:status "Active" :icon "audio-headphones" :tooltip "tinifu: idle"})
    (loop []
      (when-not @stop?
        ;; a shim/DBus failure must not kill the tray thread — log and keep
        ;; polling. recur stays outside try (Jolt forbids recur across try).
        (let [ev (try (tray/poll! 300)
                      (catch Exception e
                        (println "tinifu tray:" (.getMessage e))
                        (flush)
                        nil))]
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
                             {:status "Active" :icon "audio-headphones"
                              :tooltip (str "tinifu: " text)}))))
            :secondary (reset! stop? true)
            nil)
          (recur)))))
  (tray/close!))

(defn- ptt-transcribe-and-inject!
  "Transcribe the ptt wav and inject the text; returns {:text ...} or {:error ...}."
  [state wav]
  (swap! state assoc :state :transcribing)
  (let [r (try
            (let [res (whistle/transcribe-bytes
                       (audio/to-f32 (whistle/slurp wav)) {})]
              (when-some [text (:text res)]
                (inj/inject! text {:runner runner/run}))
              (swap! state assoc :state :idle)
              (select-keys res [:text :language]))
            (catch Exception e
              (swap! state assoc :state :idle)
              {:error (str (.getMessage e))}))]
    r))

(defn- ptt-handlers
  "Push-to-talk: ptt-start launches a recorder thread (60 s cap); ptt-end
  kills the recorder (pkill on the wav path), transcribes, and injects."
  [state]
  (let [rec-thread (atom nil)
        wav-path   (atom nil)]
    {:ptt-start-fn (fn []
                     (if (and @rec-thread (.isAlive ^Thread @rec-thread))
                       {:error "already recording"}
                       (let [wav (str "/tmp/tinifu-" (System/currentTimeMillis) ".wav")
                             t (Thread. (fn []
                                          (try
                                            (cap/record! wav {:runner runner/run :max-seconds 60})
                                            (catch Exception _))))]
                         (reset! wav-path wav)
                         (reset! rec-thread t)
                         (swap! state assoc :state :listening :since (System/currentTimeMillis))
                         (.start t)
                         {})))
     :ptt-end-fn (fn []
                   (let [t @rec-thread
                         wav @wav-path]
                     (if (and t (.isAlive ^Thread t))
                       (do
                         (runner/run ["pkill" "-TERM" "-f" wav])
                         (Thread/sleep 300)
                         (ptt-transcribe-and-inject! state wav))
                       {:error "not recording"})))}))

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
      (merge {:state-fn (fn [] @state)
              :transcribe-fn (fn [path]
                               (swap! state assoc :state :transcribing)
                               (try
                                 (let [r (whistle/transcribe-bytes (audio/to-f32 (whistle/slurp path)) {})]
                                   (swap! state assoc :state :idle)
                                   r)
                                 (catch Exception e
                                   (swap! state assoc :state :idle)
                                   {:error (str (.getMessage e))})))
              :stop-fn (fn [] (reset! stop? true) (reset! state {:state :idle}))}
             (ptt-handlers state)))))