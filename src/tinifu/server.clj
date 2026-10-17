(ns tinifu.server
  "Daemon: holds the loaded Whistle model and serves a Unix socket at
  $XDG_RUNTIME_DIR/tinifu.sock. One request per connection, one JSON line back.

  Unix-socket plumbing is POSIX via jolt.ffi (jolt.socket is IPv4-only)."
  (:require [tinifu.protocol :as proto]
            [tinifu.state :as st]
            [tinifu.whistle :as whistle]
            [jolt.ffi :as ffi]
            [clojure.string :as str]))

(ffi/load-library)

(def ^:private af-unix 1)
(def ^:private sock-stream 1)

(ffi/defcfn c-socket "socket" [:int :int :int] :int)
(ffi/defcfn c-bind "bind" [:int :pointer :int] :int)
(ffi/defcfn c-listen "listen" [:int :int] :int)
(ffi/defcfn c-accept "accept" [:int :pointer :pointer] :int :blocking)
(ffi/defcfn c-close "close" [:int] :int)
(ffi/defcfn c-read "read" [:int :pointer :size_t] :ssize_t :blocking)
(ffi/defcfn c-write "write" [:int :pointer :size_t] :ssize_t)
(ffi/defcfn c-unlink "unlink" [:string] :int)

(defn socket-path
  "Runtime socket path; override with TINIFU_SOCK."
  []
  (or (System/getenv "TINIFU_SOCK")
      (str (or (System/getenv "XDG_RUNTIME_DIR") "/tmp") "/tinifu.sock")))

(defn- sockaddr-un
  "16-zeroed bytes; family=AF_UNIX at [0..1], path from byte 2."
  [path]
  (let [b (byte-array 110)]
    (aset b 0 (unchecked-byte (bit-and af-unix 0xFF)))
    (aset b 1 0)
    (dotimes [i (min (count path) 107)]
      (aset b (+ 2 i) (unchecked-byte (int (nth path i)))))
    b))

(defn handle-request
  "Request map to a response line, given handlers. Pure in shape; the
  transcribe handler does the work."
  [{:keys [cmd path error]}
   {:keys [state-fn transcribe-fn stop-fn ptt-start-fn ptt-end-fn]}]
  (cond
    error (str "{\"error\":" (pr-str error) "}\n")
    (= :status cmd) (proto/status->line (state-fn))
    (= :stop cmd) (do (stop-fn) "{\"ok\":true}\n")
    (= :ptt-start cmd) (let [r (ptt-start-fn)]
                         (if (:error r)
                           (str "{\"error\":" (pr-str (:error r)) "}\n")
                           "{\"ok\":true}\n"))
    (= :ptt-end cmd) (let [r (ptt-end-fn)]
                       (if (:error r)
                         (str "{\"error\":" (pr-str (:error r)) "}\n")
                         (str (whistle/result->json r) "\n")))
    (= :transcribe cmd) (let [r (transcribe-fn path)]
                          (if (:error r)
                            (str "{\"error\":" (pr-str (:error r)) "}\n")
                            (str (whistle/result->json r) "\n")))
    :else "{\"error\":\"unhandled\"}\n"))

(defn- read-line!
  "Read one newline-terminated line from fd. nil on EOF with no data."
  [fd]
  (loop [acc (byte-array 0)]
    (let [buf (ffi/alloc 1)
          n (c-read fd buf 1)]
      (cond
        (<= n 0) (when (pos? (count acc)) (String. acc "UTF-8"))
        :else (let [ch (ffi/read buf :uint8 0)]
                (if (= ch (int \newline))
                  (String. acc "UTF-8")
                  (recur (byte-array (into (vec acc) [ch])))))))))

(defn serve-once!
  "Accept one connection on server fd, answer it, close it."
  [server-fd handlers]
  (let [sa (ffi/alloc 110) lenp (ffi/alloc 4)]
    (ffi/write lenp :int 110)
    (let [fd (c-accept server-fd sa lenp)]
      (when (pos? fd)
        (try
          (when-some [line (read-line! fd)]
            (let [resp (handle-request (proto/parse-request line) handlers)
                  bytes (.getBytes ^String resp "UTF-8")
                  buf (ffi/alloc (count bytes))]
              (ffi/write-array buf bytes 0 (count bytes))
              (c-write fd buf (count bytes))))
          (finally (c-close fd)))))))

(defn serve!
  "Bind, listen, and loop until a stop request arrives. Returns when stopped."
  [{:keys [path] :or {path (socket-path)}} handlers]
  (c-unlink path)
  (let [fd (c-socket af-unix sock-stream 0)]
    (when (neg? fd) (throw (ex-info "socket() failed" {})))
    (let [sa (sockaddr-un path)]
      (with-open [a (ffi/confined-arena)]
        (let [p (ffi/alloc a (alength sa))]
          (ffi/write-array p sa 0 (alength sa))
          (when (neg? (c-bind fd p (alength sa)))
            (throw (ex-info "bind failed" {:path path}))))
        (c-listen fd 4)
        (loop [running true]
          (when running
            (let [stop? (atom false)]
              ;; a handler failure must not kill the server loop
              (try (serve-once! fd (assoc handlers :stop-fn (fn [] (reset! stop? true))))
                   (catch Exception e nil))
              (recur (not @stop?)))))
        (c-close fd)
        (c-unlink path)))))
