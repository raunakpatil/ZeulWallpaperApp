package com.wallpaperapp.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wallpaperapp.app.MainActivity
import com.wallpaperapp.app.R

object PushNotifications {
    /** Intent extra carrying the push id, so the tap can be counted (`record_push_open`). */
    const val EXTRA_PUSH_ID = "zeul.push_id"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        PushChannel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, context.getString(channel.label), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(channel.description)
                },
            )
        }
    }

    /** Shows [payload]; on tap MainActivity opens its link (or Home) and records the open. */
    fun show(context: Context, payload: PushPayload, image: Bitmap?) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return // Android 13+ without permission
        val id = (payload.pushId ?: payload.title).hashCode()
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = payload.link?.let(Uri::parse)
            putExtra(EXTRA_PUSH_ID, payload.pushId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, payload.channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
        if (image != null) {
            builder.setLargeIcon(image)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null as Bitmap?))
        } else if (payload.body.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
        }
        try {
            notifications.notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and notify; nothing to show.
        }
    }
}
