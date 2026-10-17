(ns tinifu.state
  "Session state machine: idle -> listening -> transcribing -> idle.
  Pure transitions, tested.")

(defn initial [] {:state :idle})

(defn transition
  "Apply an event to the state map."
  [{:keys [state] :as s} event]
  (case event
    :start-recording (if (= :idle state) {:state :listening} s)
    :stop-recording  (case state
                       :listening {:state :transcribing}
                       s)
    :done            (if (= :transcribing state) {:state :idle} s)
    :cancel          {:state :idle}
    :error           {:state :idle :last-error true}
    s))
