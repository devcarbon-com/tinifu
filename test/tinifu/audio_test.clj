(ns tinifu.audio-test
  (:require [clojure.test :refer [deftest is testing]]
            [tinifu.audio :as audio]))

(def ^:private f32-max Float/MAX_VALUE)

(defn- f32->bytes
  "Little-endian float32 byte encoding of a sequence of sample values."
  [values]
  (byte-array
   (mapcat (fn [v]
             (let [bits (Float/floatToIntBits (float v))]
               [(unchecked-byte (bit-and bits 0xFF)) (unchecked-byte (bit-and (bit-shift-right bits 8) 0xFF))
                (unchecked-byte (bit-and (bit-shift-right bits 16) 0xFF)) (unchecked-byte (bit-and (bit-shift-right bits 24) 0xFF))]))
           values)))

(defn- pcm16->bytes
  "Little-endian int16 byte encoding of sample values in [-32768, 32767]."
  [values]
  (byte-array
   (mapcat (fn [v]
             (let [s (int v)]
               [(unchecked-byte (bit-and s 0xFF)) (unchecked-byte (bit-and (bit-shift-right s 8) 0xFF))]))
           values)))

(defn- wav-bytes
  "Minimal single-chunk RIFF/WAVE container."
  [tag channels rate width data]
  (let [fmt (byte-array (map int (concat [\f \m \t \space]
                                (audio/le-u32 16)
                                (audio/le-u16 tag) (audio/le-u16 channels)
                                (audio/le-u32 rate) (audio/le-u32 (* rate channels width))
                                (audio/le-u16 (* channels width)) (audio/le-u16 (* width 8)))))
        riff-size (+ 4 8 (count fmt) 8 (count data))]
    (byte-array (map int (concat [\R \I \F \F] (audio/le-u32 riff-size) [\W \A \V \E]
                        (seq fmt)
                        [\d \a \t \a] (audio/le-u32 (count data)) (seq data))))))

(defn- bytes= [a b] (java.util.Arrays/equals ^bytes a ^bytes b))

(deftest pcm16-mono->f32
  (is (bytes= (audio/pcm16->f32 (pcm16->bytes [0 16384 -16384]))
              (f32->bytes [0.0 0.5 -0.5]))))

(deftest to-f32-downmixes-stereo-to-mono
  ;; frames [8192,24576] and [-8192,-24576] mean to 16384 and -16384 -> ±0.5
  (let [w (wav-bytes 1 2 16000 2 (pcm16->bytes [8192 24576 -8192 -24576]))]
    (is (bytes= (audio/to-f32 w) (f32->bytes [0.5 -0.5])))))

(deftest parse-wav-16k-mono-pcm16
  (let [w (wav-bytes 1 1 16000 2 (pcm16->bytes [100 -100]))]
    (is (= {:channels 1 :rate 16000 :width 2} (select-keys (audio/parse-wav w) [:channels :rate :width])))))

(deftest parse-wav-rejects-not-wav
  (is (thrown? Exception (audio/parse-wav (byte-array 20)))))

(deftest parse-wav-rejects-unsupported-encoding
  ;; IEEE double (tag 3, width 8) is not a supported encoding
  (is (thrown? Exception (audio/parse-wav (wav-bytes 3 1 16000 8 (byte-array 8))))))

(deftest to-f32-already-16k-mono
  (let [w (wav-bytes 1 1 16000 2 (pcm16->bytes [0 16384]))]
    (is (bytes= (audio/to-f32 w) (f32->bytes [0.0 0.5])))))

(deftest to-f32-downmixes-and-converts
  (let [w (wav-bytes 1 2 16000 2 (pcm16->bytes [16384 -16384]))]
    (is (bytes= (audio/to-f32 w) (f32->bytes [0.0])))))

(deftest sample-count
  (is (= 4 (audio/sample-count (f32->bytes [0.0 0.5 -0.5 1.0])))))
