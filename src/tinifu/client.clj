(ns tinifu.client
  "Unix-socket client for the tinifu daemon: one request line in, one JSON
  line out. Socket I/O goes through jolt.ffi (POSIX socket/connect) since
  java.net.UnixDomainSocketAddress is not provided on this host."
  (:require [jolt.ffi :as ffi]))

(ffi/load-library)

(def ^:private af-unix 1)
(def ^:private sock-stream 1)

(ffi/defcfn c-socket "socket" [:int :int :int] :int)
(ffi/defcfn c-connect "connect" [:int :pointer :int] :int)
(ffi/defcfn c-close "close" [:int] :int)
(ffi/defcfn c-read "read" [:int :pointer :size_t] :ssize_t)
(ffi/defcfn c-write "write" [:int :pointer :size_t] :ssize_t)

(defn- sockaddr-un
  "16-zeroed bytes; family=AF_UNIX at [0..1], path from byte 2."
  [path]
  (let [b (byte-array 110)]
    (aset b 0 (unchecked-byte (bit-and af-unix 0xFF)))
    (aset b 1 0)
    (dotimes [i (min (count path) 107)]
      (aset b (+ 2 i) (unchecked-byte (int (nth path i)))))
    b))

(defn- recv-line
  "Read from fd until newline; the line without the newline, or nil."
  [a fd]
  (loop [acc (byte-array 0)]
    (let [buf (ffi/alloc a 4096)
          n (c-read fd buf 4096)]
      (cond
        (<= n 0) (when (pos? (alength acc)) (String. acc "UTF-8"))
        :else (let [chunk (ffi/read-array buf n)]
                (if (= (int \newline) (aget chunk (dec n)))
                  (String. (byte-array (into (vec acc) (vec chunk))) "UTF-8")
                  (recur (byte-array (into (vec acc) (vec chunk))))))))))

(defn request
  "Send one request line to socket-path, return the response line (nil on EOF)."
  [socket-path line]
  (let [fd (c-socket af-unix sock-stream 0)]
    (when (neg? fd) (throw (ex-info "socket() failed" {})))
    (try
      (with-open [a (ffi/confined-arena)]
        (let [sa (sockaddr-un socket-path)
              p (ffi/alloc a (alength sa))
              req (.getBytes ^String (str line "\n") "UTF-8")
              rp (ffi/alloc a (count req))]
          (ffi/write-array p sa 0 (alength sa))
          (when (neg? (c-connect fd p (alength sa)))
            (throw (ex-info "connect failed" {:path socket-path})))
          (ffi/write-array rp req 0 (count req))
          (c-write fd rp (count req))
          (recv-line a fd)))
      (finally (c-close fd)))))
