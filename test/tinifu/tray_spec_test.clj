(ns tinifu.tray-spec-test
  (:require [clojure.test :refer [deftest is]]
            [tinifu.tray-spec :as spec]))

(deftest service-name
  (is (= "org.kde.StatusNotifier.tinifu-123" (spec/service-name 123))))

(deftest properties-idle
  (let [p (spec/properties {:state :idle})]
    (is (= "Passive" (p "Status")))
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
