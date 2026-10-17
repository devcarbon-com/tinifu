(ns tinifu.audio
  "Pure WAV parsing and PCM16 -> float32 conversion for the Whistle engine.

  Whistle consumes 16 kHz mono float32 samples in [-1, 1]. Resampling from
  other rates is out of scope here (the adapter rejects them); input is
  expected to be 16 kHz mono or multichannel PCM16, which downmixes.")

(defn le-u16
  "Little-endian unsigned 16-bit encoding of n, as a sequence of byte literals."
  [n]
  [(bit-and n 0xFF) (bit-and (bit-shift-right n 8) 0xFF)])

(defn le-u32
  "Little-endian unsigned 32-bit encoding of n, as a sequence of byte literals."
  [n]
  [(bit-and n 0xFF) (bit-and (bit-shift-right n 8) 0xFF)
   (bit-and (bit-shift-right n 16) 0xFF) (bit-and (bit-shift-right n 24) 0xFF)])

(defn- le-u16-at ^long [^bytes b ^long at]
  (bit-or (bit-and (aget b at) 0xFF)
          (bit-shift-left (bit-and (aget b (inc at)) 0xFF) 8)))

(defn- le-u32-at ^long [^bytes b ^long at]
  (bit-or (bit-and (aget b at) 0xFF)
          (bit-shift-left (bit-and (aget b (inc at)) 0xFF) 8)
          (bit-shift-left (bit-and (aget b (+ at 2)) 0xFF) 16)
          (bit-shift-left (bit-and (aget b (+ at 3)) 0xFF) 24)))

(defn pcm16->f32
  "Raw little-endian PCM16 bytes to float32 samples in [-1, 1]."
  [^bytes raw]
  (let [n (quot (alength raw) 2)
        out (byte-array (* 4 n))]
    (dotimes [i n]
      (let [s (unchecked-short (le-u16-at raw (* 2 i)))
            v (/ (double s) 32768.0)
            bits (Float/floatToIntBits (float v))]
        (aset out (* 4 i) (unchecked-byte (bit-and bits 0xFF)))
        (aset out (+ (* 4 i) 1) (unchecked-byte (bit-and (bit-shift-right bits 8) 0xFF)))
        (aset out (+ (* 4 i) 2) (unchecked-byte (bit-and (bit-shift-right bits 16) 0xFF)))
        (aset out (+ (* 4 i) 3) (unchecked-byte (bit-and (bit-shift-right bits 24) 0xFF)))))
    out))

(defn parse-wav
  "WAV bytes to {:channels :rate :width :data} for PCM16 (tag 1) input.
  Throws on anything that is not a RIFF/WAVE PCM16 file."
  [^bytes b]
  (when (or (< (alength b) 44)
            (not= "RIFF" (String. b 0 4 "ASCII"))
            (not= "WAVE" (String. b 8 4 "ASCII")))
    (throw (ex-info "not a WAV file" {:bytes (alength b)})))
  (let [tag (le-u16-at b 20)
        channels (le-u16-at b 22)
        rate (le-u32-at b 24)
        width (quot (le-u16-at b 32) channels)]
    (when-not (and (= tag 1) (pos? channels) (pos? rate) (pos? width) (<= width 2))
      (throw (ex-info "unsupported WAV encoding (need PCM16)" {:tag tag :width width})))
    (let [n-frames (quot (- (alength b) 44) (* channels width))
          data (byte-array (* n-frames channels width))]
      (System/arraycopy b 44 data 0 (alength data))
      {:channels channels :rate rate :width width :data data})))

(defn to-f32
  "WAV bytes to raw little-endian float32 mono samples for Whistle.
  Downmixes multichannel to mono by frame mean. Non-16 kHz input throws."
  [^bytes wav]
  (let [{:keys [channels rate data]} (parse-wav wav)]
    (when-not (= 16000 rate)
      (throw (ex-info "only 16 kHz input is supported" {:rate rate})))
    (if (= 1 channels)
      (pcm16->f32 data)
      (let [n-frames (quot (alength data) (* 2 channels))
            out (byte-array (* 4 n-frames))]
        (dotimes [i n-frames]
          (let [acc (loop [c 0 sum 0]
                      (if (= c channels)
                        sum
                        (recur (inc c)
                               (+ sum (unchecked-short (le-u16-at data (+ (* 2 channels i) (* 2 c))))))))
                v (/ (double acc) (* 32768.0 channels))
                bits (Float/floatToIntBits (float v))]
            (aset out (* 4 i) (unchecked-byte (bit-and bits 0xFF)))
            (aset out (+ (* 4 i) 1) (unchecked-byte (bit-and (bit-shift-right bits 8) 0xFF)))
            (aset out (+ (* 4 i) 2) (unchecked-byte (bit-and (bit-shift-right bits 16) 0xFF)))
            (aset out (+ (* 4 i) 3) (unchecked-byte (bit-and (bit-shift-right bits 24) 0xFF)))))
        out))))

(defn sample-count
  "Number of float32 samples in a raw f32 byte buffer."
  [^bytes f32]
  (quot (alength f32) 4))
