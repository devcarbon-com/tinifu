(ns tinifu.tray-spec
  "Pure parts of the StatusNotifierItem tray: property values, introspection
  XML, and the service name. Tested; the libdbus plumbing in tinifu.tray is
  thin and live-tested via busctl.")

(defn service-name
  "SNI bus name for this tray instance."
  [pid]
  (str "org.kde.StatusNotifier.tinifu-" pid))

(def item-path "/StatusNotifierItem")

(defn item-id [] "tinifu")

(defn- q [s] (str "\"" s "\""))

(defn introspection-xml
  "Introspect reply for the item object: SNI interface with the methods a
  left-click Activate and a right-click menuless SecondaryActivate use."
  []
  (str "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\" "
       "\"http://www.freedesktop.org/standards/dbus-1.0/introspect.dtd\">"
       "<node>"
       "<interface name=\"org.freedesktop.DBus.Introspectable\">"
       "<method name=\"Introspect\"><arg name=\"data\" direction=\"out\" type=\"s\"/></method>"
       "</interface>"
       "<interface name=\"org.freedesktop.DBus.Properties\">"
       "<method name=\"Get\"><arg name=\"interface\" direction=\"in\" type=\"s\"/>"
       "<arg name=\"prop\" direction=\"in\" type=\"s\"/>"
       "<arg name=\"value\" direction=\"out\" type=\"v\"/></method>"
       "<method name=\"GetAll\"><arg name=\"interface\" direction=\"in\" type=\"s\"/>"
       "<arg name=\"props\" direction=\"out\" type=\"a{sv}\"/></method>"
       "</interface>"
       "<interface name=\"org.kde.StatusNotifierItem\">"
       "<method name=\"Activate\"><arg name=\"x\" direction=\"in\" type=\"i\"/>"
       "<arg name=\"y\" direction=\"in\" type=\"i\"/></method>"
       "<method name=\"SecondaryActivate\"><arg name=\"x\" direction=\"in\" type=\"i\"/>"
       "<arg name=\"y\" direction=\"in\" type=\"i\"/></method>"
       "<property name=\"Category\" type=\"s\" access=\"read\"/>"
       "<property name=\"Id\" type=\"s\" access=\"read\"/>"
       "<property name=\"Title\" type=\"s\" access=\"read\"/>"
       "<property name=\"Status\" type=\"s\" access=\"read\"/>"
       "<property name=\"IconName\" type=\"s\" access=\"read\"/>"
       "<property name=\"ToolTip\" type=\"(sa(iiay)ss)\" access=\"read\"/>"
       "</interface>"
       "</node>"))

(defn properties
  "Property map for the SNI interface, given current app state:
  state keyword (:idle/:listening/:transcribing/:error) and a tooltip string."
  [{:keys [state tooltip]}]
  {"Category" "ApplicationStatus"
   "Id" (item-id)
   "Title" (or tooltip "tinifu voice input")
   "Status" (case (or state :idle)
              :listening "NeedsAttention"
              :transcribing "NeedsAttention"
              :error "NeedsAttention"
              ;; waybar hides Passive items by default; idle = Active
              "Active")
   "IconName" (case (or state :idle)
                :listening "audio-input-microphone"
                :transcribing "mail-send"
                :error "dialog-error"
                "audio-headphones")
   "ToolTip" (or tooltip "tinifu voice input")})
