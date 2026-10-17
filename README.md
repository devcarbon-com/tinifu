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

## Voice input (daemon + tray + hotkey)

The daemon keeps the Whistle model loaded and serves a Unix socket; a tray
icon (SNI, via a small C shim over libdbus) shows state, and a compositor
keybinding runs one voice session: record → transcribe → type into the
focused window (wtype).

```sh
# start the daemon (loads model ~10 s, then serves + tray)
jolt -A:time -m tinifu.daemon            # or: systemctl --user start tinifu-daemon

# one-shot from a keybinding
jolt -A:time -m tinifu.activate          # record (max 30 s) → type text

# Or build standalone binaries and bind those (no jolt/cwd needed at press):
jolt -A:time build -m tinifu.ptt -o ~/bin/tinifu-ptt --direct-link
jolt -A:time build -m tinifu.activate -o ~/bin/tinifu-activate --direct-link
```

Socket protocol: one request per connection, `transcribe <wav>` | `status` |
`stop`, one JSON line back. Path: `$TINIFU_SOCK` or
`$XDG_RUNTIME_DIR/tinifu.sock`.

Keybinding snippets (sway / Hyprland / KDE, GNOME unsupported) and the
systemd user unit live in `packaging/`.

### Push-to-talk

Hold-a-key mode, works on any Wayland WM (the daemon does the recording —
compositors only need press/release bindings):

```sh
jolt -A:time -m tinifu.ptt start   # key press    → daemon starts recording
jolt -A:time -m tinifu.ptt end     # key release  → transcribe + type
```

sway (using the prebuilt `~/bin/tinifu-ptt` binary): `bindsym KP_0 exec ...
ptt start` + `bindsym --release KP_0 exec ... ptt end` (numpad 0 as the hold
key; a `$mod+Shift+minus` / `--release` chord works too — both can be bound
side by side). Hyprland: `bind` / `bindr` pair with `KP_0`. See
`packaging/keybindings.conf`.

## Layout

- `tinifu.audio` — pure WAV parsing and PCM16→float32 conversion (tested).
- `tinifu.whistle` — pure result-JSON parsing and options (tested) over a thin
  FFI layer: `needle_load` / `needle_transcribe` / `needle_last_error`.
- `tinifu.transcribe` — CLI wiring.
- `tinifu.protocol` / `tinifu.state` — socket line protocol and daemon state
  machine (pure, tested).
- `tinifu.capture` / `tinifu.inject` — pw-record/arecord and wtype/ydotool
  command construction (pure, tested; runners injected).
- `tinifu.server` / `tinifu.client` — POSIX AF_UNIX server/client via jolt.ffi.
- `tinifu.session` / `tinifu.activate` — one voice session; hotkey entry point.
- `tinifu.tray` + `shim/sni.c` — SNI tray icon; the C shim owns the DBus
  connection and all marshalling (jolt.ffi cannot safely drive
  DBusMessageIter directly). Build:
  `gcc -shared -fPIC -O2 -I/usr/include/dbus-1.0 -I/usr/lib/dbus-1.0/include shim/sni.c -o shim/libtinifu_sni.so -ldbus-1`
- `tinifu.daemon` — entry point: model + socket server + tray loop.

## deps

`clojure.data.json` (2.5.1) needs a `java.time.format.DateTimeFormatter`
provider on Jolt; the `:time` alias supplies the real
`io.github.jolt-lang/time` library, hence `jolt -A:time ...` everywhere.

## Tests

```sh
jolt -A:time -e "(require 'clojure.test) (require 'tinifu.audio-test) (require 'tinifu.whistle-test) (apply clojure.test/run-tests ['tinifu.audio-test 'tinifu.whistle-test])"
```
