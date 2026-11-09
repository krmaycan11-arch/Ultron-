package com.ultron.assistant

import android.service.notification.NotificationListenerService

/** Lets Ultron read current notifications once the user grants notification access. */
class NotifService : NotificationListenerService() {
    companion object { var inst: NotifService? = null }
    override fun onListenerConnected() { inst = this }
    override fun onListenerDisconnected() { inst = null }
}
