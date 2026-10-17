(ns tinifu.inject
  "Text injection into the focused Wayland window: wtype (virtual-keyboard,
  wlroots compositors) with ydotool fallback. Command construction is pure and
  tested; the runner is injected.")

(defn wtype-command [text] ["wtype" "--" text])
(defn ydotool-command [text] ["ydotool" "type" text])

(defn inject-command
  "Command vector for tool, given text."
  [tool text]
  (case tool :wtype (wtype-command text) :ydotool (ydotool-command text)))

(defn inject!
  "Type text with the first tool whose command exits zero.
  runner: (fn [cmd-vector]) -> {:exit n}. Returns the tool used."
  [text {:keys [runner available]}]
  (loop [[tool & more] (or available [:wtype :ydotool])]
    (when tool
      (let [{:keys [exit]} (runner (inject-command tool text))]
        (if (zero? exit) tool (recur more))))))
