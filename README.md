# tinifu

Speech-to-text for Linux that types into whatever window has focus. Hold a
hotkey, speak, and your words appear as keystrokes — push-to-talk style, on
any Wayland compositor. It also works as a plain CLI that transcribes WAV
files.

Fully local: the Cactus Whistle model runs on-device through a native engine.
No cloud, no account, no Python.

## Install

**Easiest: grab the prebuilt binary** from
[GitHub Releases](https://github.com/devcarbon-com/tinifu/releases). It is
self-contained — the speech engine, model, and tray shim are all embedded, so
there is nothing else to download.

```sh
tar -xzf tinifu-*-linux-x86_64.tar.gz
sudo mv tinifu /usr/local/bin/
```

**Or from source** — needs [Jolt](https://github.com/jolt-lang) ≥ 0.8 on your
PATH:

```sh
git clone https://github.com/devcarbon-com/tinifu && cd tinifu
./scripts/setup.sh   # downloads the engine + model, builds the tray shim
```

You'll also want for the voice-input features:

- `pw-record` (PipeWire) or `arecord` — recording
- `wtype` (or `ydotool`) — typing text
- (optional) a tray-capable bar (waybar etc.) — tray icon

## Quick start

Transcribe a WAV file:

```sh
tinifu transcribe clip.wav
```

Or start the voice daemon and set up a hotkey:

```sh
tinifu daemon            # loads the model (~10 s), then serves + tray icon
```

Bind a key in your compositor (snippets for sway, Hyprland, and KDE are in
[`packaging/keybindings.conf`](packaging/keybindings.conf)):

- **Press-to-talk** — `tinifu activate`: record (up to 30 s), transcribe,
  type into the focused window.
- **Hold-to-talk** — bind two events to one key:

  ```sh
  tinifu ptt start   # key press    → daemon starts recording
  tinifu ptt end     # key release  → transcribe + type
  ```

That's it — hold the key, talk, release, and the text lands where you're
typing.

To run the daemon automatically at login:

```sh
cp packaging/tinifu-daemon.service ~/.config/systemd/user/
systemctl --user enable --now tinifu-daemon
```

## CLI

```sh
tinifu transcribe clip.wav
tinifu transcribe clip.wav --timestamps
tinifu transcribe clip.wav --lang de --keywords "Küche,Licht"
```

Input must be a 16 kHz WAV (PCM16, mono or multichannel — channels downmix to
mono). Convert when needed: `sox in.wav -r 16000 -c 1 out.wav`.
`--timestamps` appends one `start-end word` line per word.

All commands: `transcribe`, `daemon`, `activate`, `ptt`. Run `tinifu` with no
arguments for usage.

## Notes

- The daemon serves a Unix socket (`$TINIFU_SOCK` or
  `$XDG_RUNTIME_DIR/tinifu.sock`) with one JSON-line response per request:
  `transcribe <wav>` | `status` | `stop`.
- A tray icon (SNI) shows daemon state, for bars that support it. Prebuilt
  binaries carry the shim; from source, `scripts/setup.sh` builds it.
- From source, commands run as `jolt -A:time -m tinifu.main <command>`; build
  a standalone binary with
  `jolt -A:time build -m tinifu.main -o ~/bin/tinifu --direct-link`
  (engine + model embedded, no jolt or repo needed afterwards).

## Development

- Tests: `jolt -A:time -e "(require 'clojure.test) (require 'tinifu.audio-test) (require 'tinifu.whistle-test) (apply clojure.test/run-tests ['tinifu.audio-test 'tinifu.whistle-test])"`
- Layout: pure, tested logic in `tinifu.audio` (WAV parsing), `tinifu.whistle`
  (result parsing + FFI), `tinifu.protocol`/`tinifu.state` (socket protocol,
  daemon state machine), `tinifu.capture`/`tinifu.inject` (pw-record and
  wtype command construction); side effects in `tinifu.server`/`tinifu.client`
  (AF_UNIX via jolt.ffi), `tinifu.session` (one voice session), `tinifu.tray`
  + `shim/sni.c` (SNI tray over a small C shim), and the entry points
  `tinifu.transcribe`, `tinifu.daemon`, `tinifu.activate`, `tinifu.ptt`,
  dispatched by `tinifu.main`.
- Releasing: `./scripts/release.sh v1.2.3` builds the binary (vendored engine
  + model and the tray shim embedded — `vendor/` is gitignored and staged by
  `scripts/setup.sh`, `shim/libtinifu_sni.so` is committed), tars it, and
  publishes a GitHub release.
