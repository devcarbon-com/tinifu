# tinifu

Speech-to-text on Jolt: a Clojure-on-Chez CLI that transcribes WAV files with
Cactus's Whistle model (from `~/projects/tinifu/needle`), calling the native
`libneedle.so` engine directly through `jolt.ffi` — no Python involved.

## Run

```sh
cd ~/projects/tinifu
jolt -A:time -m tinifu.transcribe clip.wav
jolt -A:time -m tinifu.transcribe clip.wav --timestamps
jolt -A:time -m tinifu.transcribe clip.wav --lang de --keywords "Küche,Licht"
```

Input must be a 16 kHz WAV (PCM16, mono or multichannel — channels downmix to
mono). Convert with `sox in.wav -r 16000 -c 1 out.wav` when needed.

Output is the transcript; `--timestamps` appends `start-end word` lines.

## Layout

- `tinifu.audio` — pure WAV parsing and PCM16→float32 conversion (tested).
- `tinifu.whistle` — pure result-JSON parsing and options (tested) over a thin
  FFI layer: `needle_load` / `needle_transcribe` / `needle_last_error`.
- `tinifu.transcribe` — CLI wiring.

## deps

`clojure.data.json` (2.5.1) needs a `java.time.format.DateTimeFormatter`
provider on Jolt; the `:time` alias supplies the real
`io.github.jolt-lang/time` library, hence `jolt -A:time ...` everywhere.

## Tests

```sh
jolt -A:time -e "(require 'clojure.test) (require 'tinifu.audio-test) (require 'tinifu.whistle-test) (apply clojure.test/run-tests ['tinifu.audio-test 'tinifu.whistle-test])"
```
