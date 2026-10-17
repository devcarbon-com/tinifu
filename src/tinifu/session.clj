(ns tinifu.session
  "One voice-input session: record with the microphone, transcribe via the
  daemon (or in-process fallback), inject the text into the focused window.
  All I/O behind injected components; the flow itself is testable."
  (:require [tinifu.capture :as cap]
            [tinifu.inject :as inj]
            [tinifu.runner :as runner]
            [tinifu.state :as st]
            [tinifu.whistle :as whistle]))

(defn run-session!
  "Full loop: capture to a temp WAV, transcribe, type the text.
  opts: :transcribe-fn (path) -> {:text ...} | {:error ...},
        :capture-opts / :inject-opts (injected runners), :tmp-dir.
  Returns {:text ... :injector tool} or {:error ...}."
  [{:keys [transcribe-fn capture-opts inject-opts tmp-dir]}]
  (let [wav (str (or tmp-dir "/tmp") "/tinifu-" (System/currentTimeMillis) ".wav")]
    (if-some [tool (cap/record! wav capture-opts)]
      (let [result (transcribe-fn wav)
            _ (doto (java.io.File. wav) (.deleteOnExit))]
        (if-some [err (:error result)]
          {:error err}
          (if-some [injector (inj/inject! (:text result) inject-opts)]
            {:text (:text result) :injector injector}
            {:text (:text result) :error "no injector available"})))
      {:error "no recorder available"})))

(defn cli-session!
  "Session with real process runners and daemon-backed transcription.
  :max-seconds caps the recording (default 30)."
  [{:keys [sock-path max-seconds]
    :or {sock-path (or (System/getenv "TINIFU_SOCK")
                       (str (or (System/getenv "XDG_RUNTIME_DIR") "/tmp")
                            "/tinifu.sock"))
         max-seconds 30}}]
  (require 'tinifu.client)
  (let [client (ns-resolve 'tinifu.client 'request)]
    (run-session!
     {:transcribe-fn (fn [path]
                       (let [resp (client sock-path (str "transcribe " path))]
                         (if resp
                           (whistle/parse-result resp)
                           {:error "daemon unreachable"})))
      :capture-opts {:runner runner/run :max-seconds max-seconds}
      :inject-opts {:runner runner/run}})))
