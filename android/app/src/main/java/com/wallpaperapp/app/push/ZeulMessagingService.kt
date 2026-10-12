package com.wallpaperapp.app.push

import coil3.SingletonImageLoader
import android.annotation.SuppressLint
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives data-only topic pushes (CONTRACTS §8) and shows them itself, foreground or background.
 * Topic messaging only, so device tokens are never stored or sent anywhere.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh") // topic-only: there is no token to keep in sync
class ZeulMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.from(message.data) ?: return
        // Runs on a background thread with ~10 s to finish: fetch the image, but never wait long.
        val image = payload.imageUrl?.let { url ->
            runBlocking {
                withTimeoutOrNull(6_000) {
                    val request = ImageRequest.Builder(this@ZeulMessagingService).data(url).size(1080).allowHardware(false).build()
                    (SingletonImageLoader.get(this@ZeulMessagingService).execute(request) as? SuccessResult)?.image?.toBitmap()
                }
            }
        }
        PushNotifications.show(this, payload, image)
    }
}
