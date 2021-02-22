package com.raven.ravenfcmutil.notification

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raven.ravenandroidsdk.RavenSdk
import com.raven.ravenandroidsdk.models.Status


class NotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.extras?.getString(RavenSdk.RAVEN_NOTIFICATION_ID)
        val clickAction = intent.extras?.getString("click_action")

        val resultIntent = Intent(clickAction)
        val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(resultIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        resultPendingIntent?.send()

        if (null != clickAction) {
            notificationId?.let { RavenSdk.updateStatus(it, Status.CLICKED) }
        }
    }
}