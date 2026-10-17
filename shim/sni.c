/* sni.c — minimal StatusNotifierItem service over libdbus for tinifu.
 *
 * Owns the DBusConnection and all DBusMessageIter marshalling (opaque struct,
 * pointer-to-pointer value args) that pure Jolt FFI cannot express. Exposes a
 * flat imperative API:
 *
 *   sni_init(name)            connect to session bus, request `name`
 *   sni_set_state(status, icon, tooltip)   update item properties
 *   sni_poll(timeout_ms)      pump the bus, serve Introspect/Properties,
 *                             return 1=Activate, 2=SecondaryActivate, 0=none
 *   sni_close()               release
 */
#include <dbus/dbus.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

static DBusConnection *conn = NULL;
static DBusError err;
static char item_status[32] = "Passive";
static char item_icon[128] = "audio-headphones";
static char item_tooltip[256] = "tinifu voice input";
static int activate_flag = 0, secondary_flag = 0;

static const char *INTROSPECTION_XML =
"<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\" "
"\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">"
"<node>"
"<interface name=\"org.freedesktop.DBus.Introspectable\">"
"<method name=\"Introspect\"><arg name=\"data\" direction=\"out\" type=\"s\"/></method>"
"</interface>"
"<interface name=\"org.freedesktop.DBus.Properties\">"
"<method name=\"Get\"><arg name=\"interface\" direction=\"in\" type=\"s\"/>"
"<arg name=\"prop\" direction=\"in\" type=\"s\"/>"
"<arg name=\"value\" direction=\"out\" type=\"v\"/></method>"
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
"<property name=\"ToolTip\" type=\"s\" access=\"read\"/>"
"</interface>"
"</node>";

static void reply_string(DBusMessage *msg, const char *s) {
    DBusMessage *reply = dbus_message_new_method_return(msg);
    DBusMessageIter iter;
    dbus_message_iter_init_append(reply, &iter);
    dbus_message_iter_append_basic(&iter, DBUS_TYPE_STRING, &s);
    dbus_connection_send(conn, reply, NULL);
    dbus_connection_flush(conn);
    dbus_message_unref(reply);
}

/* Properties.Get for our SNI interface: wrap the string in a variant. */
static void reply_variant_string(DBusMessage *msg, const char *s) {
    DBusMessage *reply = dbus_message_new_method_return(msg);
    DBusMessageIter iter, var;
    dbus_message_iter_init_append(reply, &iter);
    dbus_message_iter_open_container(&iter, DBUS_TYPE_VARIANT, "s", &var);
    dbus_message_iter_append_basic(&var, DBUS_TYPE_STRING, &s);
    dbus_message_iter_close_container(&iter, &var);
    dbus_connection_send(conn, reply, NULL);
    dbus_connection_flush(conn);
    dbus_message_unref(reply);
}

static const char *prop_value(const char *prop) {
    if (!strcmp(prop, "Category")) return "SystemServices";
    if (!strcmp(prop, "Id")) return "tinifu";
    if (!strcmp(prop, "Title")) return "tinifu voice input";
    if (!strcmp(prop, "Status")) return item_status;
    if (!strcmp(prop, "IconName")) return item_icon;
    if (!strcmp(prop, "ToolTip")) return item_tooltip;
    return NULL;
}

static void handle_message(DBusMessage *msg) {
    const char *iface = dbus_message_get_interface(msg);
    const char *member = dbus_message_get_member(msg);
    const char *path = dbus_message_get_path(msg);

    if (dbus_message_is_method_call(msg, "org.freedesktop.DBus.Introspectable", "Introspect")) {
        reply_string(msg, INTROSPECTION_XML);
    } else if (dbus_message_is_method_call(msg, "org.freedesktop.DBus.Properties", "Get")
               && path && !strcmp(path, "/StatusNotifierItem")) {
        DBusMessageIter iter;
        char prop[128] = "";
        dbus_message_iter_init(msg, &iter);
        /* arg0 = interface (skip), arg1 = property name */
        if (dbus_message_iter_next(&iter)
            && dbus_message_iter_get_arg_type(&iter) == DBUS_TYPE_STRING) {
            const char *p = NULL;
            dbus_message_iter_get_basic(&iter, &p);
            if (p) strncpy(prop, p, sizeof(prop) - 1);
        }
        const char *v = prop_value(prop);
        if (v) reply_variant_string(msg, v);
        /* unhandled property: no reply; caller treats it as an error */
    } else if (dbus_message_is_method_call(msg, "org.kde.StatusNotifierItem", "Activate")) {
        activate_flag = 1;
        reply_string(msg, "");
    } else if (dbus_message_is_method_call(msg, "org.kde.StatusNotifierItem", "SecondaryActivate")) {
        secondary_flag = 1;
        reply_string(msg, "");
    }
    (void)iface; (void)member;
}

int sni_init(const char *name) {
    dbus_error_init(&err);
    conn = dbus_bus_get(DBUS_BUS_SESSION, &err);
    if (!conn || dbus_error_is_set(&err)) return -1;
    int r = dbus_bus_request_name(conn, name, DBUS_NAME_FLAG_REPLACE_EXISTING, &err);
    if (dbus_error_is_set(&err)) return -2;
    /* Hosts (waybar etc.) only show items they know about; register ourselves. */
    DBusMessage *reg = dbus_message_new_method_call(
        "org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher",
        "org.kde.StatusNotifierWatcher", "RegisterStatusNotifierItem");
    if (reg) {
        /* per spec the arg is the item's object path; waybar also accepts
         * the bus name — send the path form */
        const char *arg = "/StatusNotifierItem";
        dbus_message_append_args(reg, DBUS_TYPE_STRING, &arg,
                                 DBUS_TYPE_INVALID);
        dbus_connection_send(conn, reg, NULL);
        dbus_connection_flush(conn);
        dbus_message_unref(reg);
    }
    return r; /* 1 = primary owner */
}

void sni_set_state(const char *status, const char *icon, const char *tooltip) {
    if (status) strncpy(item_status, status, sizeof(item_status) - 1);
    if (icon) strncpy(item_icon, icon, sizeof(item_icon) - 1);
    if (tooltip) strncpy(item_tooltip, tooltip, sizeof(item_tooltip) - 1);
}

int sni_poll(int timeout_ms) {
    if (!conn) return -1;
    /* read_write_dispatch also drains the dispatch queue; plain read_write
     * can leave incoming messages unconsumed (observed: outgoing backlog
     * grows and the item stops answering after ~20 s of idle polling). */
    dbus_connection_read_write_dispatch(conn, timeout_ms);
    DBusMessage *msg;
    while ((msg = dbus_connection_pop_message(conn)) != NULL) {
        handle_message(msg);
        dbus_message_unref(msg);
    }
    if (activate_flag) { activate_flag = 0; return 1; }
    if (secondary_flag) { secondary_flag = 0; return 2; }
    return 0;
}

void sni_close(void) {
    /* The session-bus connection is shared with the rest of the process:
     * dbus_connection_close() on it is forbidden. Just drop our reference. */
    conn = NULL;
    dbus_error_free(&err);
}
