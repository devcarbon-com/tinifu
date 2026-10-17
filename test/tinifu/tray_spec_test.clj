(ns tinifu.tray-spec-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [tinifu.tray :as tray]
            [tinifu.tray-spec :as spec]))

(deftest service-name
  (is (= "org.kde.StatusNotifier.tinifu-123" (spec/service-name 123))))

(deftest properties-idle
  (let [p (spec/properties {:state :idle})]
    (is (= "Active" (p "Status")))
    (is (= "ApplicationStatus" (p "Category")))
    (is (= "audio-headphones" (p "IconName")))))

(deftest properties-listening
  (let [p (spec/properties {:state :listening})]
    (is (= "NeedsAttention" (p "Status")))
    (is (= "audio-input-microphone" (p "IconName")))))

(deftest introspection-names-interfaces
  (let [xml (spec/introspection-xml)]
    (is (.contains ^String xml "org.kde.StatusNotifierItem"))
    (is (.contains ^String xml "Activate"))
    (is (.contains ^String xml "Introspectable"))))

(deftest shim-path-resolves
  ;; in a built binary the shim is an embedded resource (extracted to a temp
  ;; file); in a checkout it is the committed shim/libtinifu_sni.so
  (is (string? (tray/shim-path))))
