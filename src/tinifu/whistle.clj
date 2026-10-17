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

(ffi/load-library "/home/pacman/.cache/cactus-needle/v3/3.3.1/libneedle.so")

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

(defn transcribe-bytes
  "Raw little-endian float32 mono samples (16 kHz) to a parsed result map.
  language/keywords as in `options`; word-timestamps adds :words."
  [^bytes f32 {:keys [language keywords word-timestamps weights]}]
  (load-model! (or weights "/home/pacman/.cache/cactus-needle/whistle/2.0.0/whistle.cact"))
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
