(ns tinifu.tray
  "StatusNotifierItem tray via the C shim (shim/sni.c, compiled to
  shim/libtinifu_sni.so). The shim owns the DBusConnection and all
  DBusMessageIter marshalling; Jolt drives it through a flat API:
  init / set-state / poll (returns 1=Activate, 2=SecondaryActivate) / close."
  (:require [jolt.ffi :as ffi]))

(defn- shim-path
  "Absolute path to libtinifu_sni.so, so the binary/jar can run from any cwd:
  $TINIFU_SHIM, then ~/.local/lib/tinifu/, then the project dir (dev)."
  []
  (or (System/getenv "TINIFU_SHIM")
      (let [installed (str (System/getProperty "user.home")
                           "/.local/lib/tinifu/libtinifu_sni.so")]
        (if (.exists (java.io.File. installed))
          installed
          (str (System/getProperty "user.dir") "/shim/libtinifu_sni.so")))))

(ffi/load-library (shim-path))

(ffi/defcfn sni-init "sni_init" [:string] :int)
(ffi/defcfn sni-set-state "sni_set_state" [:string :string :string] :void)
;; :blocking is essential: sni_poll parks up to timeout-ms inside C. Without
;; it the cooperative fiber scheduler stalls and the daemon's socket server
;; thread starves.
(ffi/defcfn sni-poll "sni_poll" [:int] :int :blocking)
(ffi/defcfn sni-close "sni_close" [] :void)

(defn init!
  "Register the SNI name on the session bus. Returns the request result
  (1 = primary owner)."
  [name]
  (sni-init name))

(defn set-state!
  "Push status (\"Passive\"/\"NeedsAttention\"), icon name, tooltip."
  [{:keys [status icon tooltip]}]
  (sni-set-state status icon tooltip))

(defn poll!
  "Pump the bus for up to timeout-ms; answer :activate, :secondary, or nil."
  [timeout-ms]
  (case (sni-poll timeout-ms)
    1 :activate
    2 :secondary
    nil))

(defn close! [] (sni-close))
