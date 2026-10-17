(ns tinifu.whistle
  "Adapter over the Whistle speech-to-text engine (libneedle.so), reached
  through jolt.ffi. Pure result parsing lives here and is tested with fixture
  strings; the FFI calls are thin and grouped in engine fns."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jolt.ffi :as ffi]))

(def sample-rate 16000)
(def output-bytes-per-second 4096)

(defn parse-result
  "Engine JSON envelope to a keywordized map: :text :language :ttft-ms
  :decode-tps, plus :words (word/start/end/probability) when present."
  [s]
  (let [m (json/read-str s :key-fn keyword)]
    (cond-> {:text (get m :text "")
             :language (get m :language)
             :ttft-ms (get m :ttft_ms)
             :decode-tps (get m :decode_tps)}
      (contains? m :words)
      (assoc :words (mapv #(select-keys % [:word :start :end :probability]) (:words m))))))

(defn options
  "Language and keywords to engine C-string arguments. nil means detect;
  a keyword collection joins one per line."
  [language keywords]
  [(when language (name language))
   (cond
     (nil? keywords) nil
     (string? keywords) keywords
     :else (str/join "\n" keywords))])

(defn- cache-dir
  "Cactus cache dir: $TINIFU_CACHE, then $XDG_CACHE_HOME/cactus-needle, then
  ~/.cache/cactus-needle (where scripts/setup.sh puts the engine and model)."
  []
  (or (System/getenv "TINIFU_CACHE")
      (some-> (System/getenv "XDG_CACHE_HOME")
              (as-> xdg (str xdg "/cactus-needle")))
      (let [home (System/getProperty "user.home")]
        (str home "/.cache/cactus-needle"))))

(defn- resource-file!
  "Path to a bundled resource: as-is when on disk, extracted to a temp file
  when embedded in a built binary (a baked resource has no filesystem path;
  its URL reports protocol jar)."
  [name]
  (when-let [url (io/resource name)]
    (if (= "jar" (.getProtocol url))
      (let [f (java.io.File/createTempFile
               "tinifu-" (str "-" (last (str/split name #"/"))))]
        (io/copy (io/input-stream url) f)
        (.deleteOnExit f)
        (.getAbsolutePath f))
      (.getPath url))))

(defn engine-source
  "Path to libneedle.so: the bundled copy when present (in a built binary or
  a checkout with vendor/ staged), else the setup.sh cache location."
  []
  (or (resource-file! "libneedle.so")
      (str (cache-dir) "/v3/3.3.1/libneedle.so")))

(defn model-source
  "Path to whistle.cact, explicit arg first, then bundled, then cache."
  [weights]
  (or weights
      (resource-file! "whistle.cact")
      (str (cache-dir) "/whistle/2.0.0/whistle.cact")))

(ffi/load-library (engine-source))

(ffi/defcfn needle-load "needle_load" [:pointer :uint64] :int)
(ffi/defcfn needle-transcribe "needle_transcribe"
  [:pointer :int :string :string :int :pointer :int] :int)
(ffi/defcfn needle-last-error "needle_last_error" [] :string)

(defn- slurp-bytes [path]
  (with-open [in (io/input-stream path)
              ba (java.io.ByteArrayOutputStream.)]
    (io/copy in ba)
    (.toByteArray ba)))

(def slurp slurp-bytes)

(defonce ^:private loaded-model (atom nil))

(defn load-model!
  "Load the whistle.cact weights once per process."
  [weights-path]
  (when-not (= weights-path @loaded-model)
    (let [data (slurp-bytes weights-path)]
      (with-open [a (ffi/confined-arena)]
        (let [p (ffi/alloc a (count data))]
          (ffi/write-array p data 0 (count data))
          (when (neg? (needle-load p (count data)))
            (throw (ex-info "whistle model load failed"
                            {:path weights-path :error (ffi/ptr->string (needle-last-error))})))))
      (reset! loaded-model weights-path))))

(defn result->json
  "Result map back to the engine's JSON envelope shape."
  [{:keys [text language ttft-ms decode-tps words]}]
  (str "{\"text\":" (json/write-str (or text ""))
       ",\"language\":" (json/write-str (or language "en"))
       ",\"ttft_ms\":" (or ttft-ms 0)
       ",\"decode_tps\":" (or decode-tps 0)
       (when words (str ",\"words\":" (json/write-str words)))
       "}"))

(defn transcribe-bytes
  "Raw little-endian float32 mono samples (16 kHz) to a parsed result map.
  language/keywords as in `options`; word-timestamps adds :words."
  [^bytes f32 {:keys [language keywords word-timestamps weights]}]
  (load-model! (model-source weights))
  (let [n (quot (alength f32) 4)
        [lang kws] (options language keywords)
        out-bytes (max 65536 (* (quot n sample-rate) output-bytes-per-second))]
    (with-open [a (ffi/confined-arena)]
      (let [sp (ffi/alloc a (alength f32))
            out (ffi/alloc a out-bytes)]
        (ffi/write-array sp f32 0 (alength f32))
        (let [rc (needle-transcribe sp n lang kws (if word-timestamps 1 0) out out-bytes)]
          (when (neg? rc)
            (throw (ex-info "whistle transcription failed"
                            {:error (ffi/ptr->string (needle-last-error))})))
          (parse-result (ffi/ptr->string out)))))))
