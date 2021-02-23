package com.raven.ravenfcmutil.notification

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.FutureTarget
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.raven.ravenandroidsdk.RavenSdk
import com.raven.ravenandroidsdk.models.Status
import com.raven.ravenfcmutil.R
import kotlinx.coroutines.*
import java.util.*


class MyFirebaseMessagingService: FirebaseMessagingService() {

    //coroutine for downloading images
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {

            val notificationId = remoteMessage.data[RavenSdk.RAVEN_NOTIFICATION_ID]

            //update status to raven that the message is delivered
            notificationId?.let { RavenSdk.updateStatus(notificationId, Status.DELIVERED) }

            sendNotification(
                notificationId,
                remoteMessage.data["title"],
                remoteMessage.data["body"],
                remoteMessage.data["click_action"],
                remoteMessage.data["large_icon"],
                remoteMessage.data["big_picture"]
            )
        }
    }


    override fun onDeletedMessages() {
        super.onDeletedMessages()
        //notify to fetch long pending messages
    }


    override fun onNewToken(token: String) {
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        token.let { RavenSdk.setDeviceToken(it) }
    }


    private fun sendNotification(
        ravenNotificationId: String?,
        title: String?, messageBody: String?, clickAction: String?,
        largeIcon: String? = null, bigPicture: String? = null
    ) {

        var style: NotificationCompat.Style = NotificationCompat.BigTextStyle().bigText(messageBody)

        //fetch largeicon and bigpicture
        if (bigPicture != null && largeIcon != null) {
            coroutineScope.launch {

                val largeIconBitmap = loadImageAsBitmap(largeIcon)
                val bigPictureBitmap = loadImageAsBitmap(bigPicture)
                style = NotificationCompat.BigPictureStyle().bigPicture(bigPictureBitmap).bigLargeIcon(
                    null
                )

                withContext(Dispatchers.Main) {
                    notify(
                        ravenNotificationId,
                        title,
                        messageBody,
                        clickAction,
                        largeIconBitmap,
                        style
                    )
                }
            }
        } else if (bigPicture != null) {
            coroutineScope.launch {
                val bigPictureBitmap = loadImageAsBitmap(bigPicture)
                style = NotificationCompat.BigPictureStyle().bigPicture(bigPictureBitmap).bigLargeIcon(
                    null
                )

                withContext(Dispatchers.Main) {
                    notify(ravenNotificationId, title, messageBody, clickAction, null, style)
                }
            }
        } else if (largeIcon != null) {
            coroutineScope.launch {
                val largeIconBitmap = loadImageAsBitmap(largeIcon)
                withContext(Dispatchers.Main) {
                    notify(
                        ravenNotificationId,
                        title,
                        messageBody,
                        clickAction,
                        largeIconBitmap,
                        style
                    )
                }
            }
        } else {
            notify(ravenNotificationId, title, messageBody, clickAction, null, style)
        }
    }


    private fun notify(
        ravenNotificationId: String?,
        title: String?, messageBody: String?, clickAction: String?,
        largeIcon: Bitmap? = null, style: NotificationCompat.Style? = null
    ) {

        //

        val channelId = "Default"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(getSmallIcon())
            .setContentTitle(title)
            .setContentText(messageBody)
            .setLargeIcon(largeIcon)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(createOnClickedIntent(this, ravenNotificationId, clickAction))
            .setDeleteIntent(createOnDismissedIntent(this, ravenNotificationId))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(
            Date().time.toInt() /* ID of notification */,
            notificationBuilder.build()
        )
    }


    private fun createOnDismissedIntent(context: Context, notificationId: String?): PendingIntent? {
        val intent = Intent(context, NotificationDismissedReceiver::class.java)
        intent.putExtra(RavenSdk.RAVEN_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            Date().time.toInt(),
            intent,
            0
        )
    }


    private fun createOnClickedIntent(
        context: Context,
        notificationId: String?,
        clickAction: String?
    ): PendingIntent? {
        val intent = Intent(context, NotificationClickReceiver::class.java)
        intent.putExtra(RavenSdk.RAVEN_NOTIFICATION_ID, notificationId)
        intent.putExtra("click_action", clickAction)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            Date().time.toInt(),
            intent,
            0
        )
    }


    private suspend fun loadImageAsBitmap(url: String?): Bitmap? {
        return coroutineScope.async {
            try {
                val futureTarget: FutureTarget<Bitmap?>? = Glide.with(this@MyFirebaseMessagingService)
                    .asBitmap()
                    .load(url)
                    .submit()

                return@async futureTarget?.get()
            }
            catch (ex: Exception) {
                ex.printStackTrace()
            }

            return@async null
        }.await()
    }


    private fun getSmallIcon(): Int {
        val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val bundle = ai.metaData
        val smallIcon: Int = bundle.getInt("notification_small_icon")
        return smallIcon
    }


    companion object {

        /*
        Invoke once after login or every app launch after login to be safe
         */
        fun setFirebaseToken() {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("Firebase", "getToken failed", task.exception)
                        return@OnCompleteListener
                    }

                    // Get new Instance ID token
                    val token: String? = task.result
                    token?.let { RavenSdk.setDeviceToken(it) }
                })
        }


        fun isAppRestricted(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return activityManager?.isBackgroundRestricted ?: false
            }

            return false
        }
    }

}