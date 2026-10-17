(ns tinifu.protocol
  "Line-oriented Unix-socket protocol for the tinifu daemon.

  Request lines:  \"transcribe <wavpath>\"  |  \"status\"  |  \"stop\"
  Response: one JSON line, terminated by a newline."
  (:require [clojure.string :as str]))

(defn parse-request
  "Request line string to a command map, or {:error \"...\"}."
  [line]
  (let [line (str/trim (or line ""))
        [verb & rest] (str/split line #"\s+" 2)]
    (case verb
      "transcribe" (let [path (str/trim (or (first rest) ""))]
                     (if (seq path) {:cmd :transcribe :path path}
                         {:error "transcribe needs a path"}))
      "status" {:cmd :status}
      "stop" {:cmd :stop}
      "ptt-start" {:cmd :ptt-start}
      "ptt-end" {:cmd :ptt-end}
      {:error (str "unknown request: " line)})))

(defn status->line
  "State map to a status JSON line."
  [{:keys [state since]}]
  (str "{\"state\":\"" (name (or state :idle)) "\",\"since\":" (or since 0) "}\n"))
