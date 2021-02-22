package com.raven.ravenfcmutil.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raven.ravenandroidsdk.RavenSdk
import com.raven.ravenandroidsdk.models.Status


class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.extras?.getString(RavenSdk.RAVEN_NOTIFICATION_ID)
        notificationId?.let { RavenSdk.updateStatus(it, Status.DISMISSED) }
    }
}