package com.wallpaperapp.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.wallpaperapp.app.BuildConfig
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps FCM topic subscriptions in line with the user's notification settings. */
@Singleton
class PushSubscriptions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** False when the build has no google-services.json (e.g. CI without the secret). */
    private val firebaseAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    fun start() {
        if (!firebaseAvailable) {
            Log.i(TAG, "Firebase not configured in this build; push disabled")
            return
        }
        val messaging = FirebaseMessaging.getInstance()
        // Everyone gets the rare announcements topic (CONTRACTS §8).
        messaging.subscribeToTopic(PushTopics.name(PushTopics.ALL, BuildConfig.DEBUG))
        scope.launch {
            combine(preferences.pushNewDrops, preferences.pushDaily) { drops, daily -> drops to daily }
                .distinctUntilChanged()
                .collect { (drops, daily) ->
                    messaging.setTopic(PushTopics.NEW_DROPS, drops)
                    messaging.setTopic(PushTopics.DAILY, daily)
                }
        }
    }

    private fun FirebaseMessaging.setTopic(topic: String, subscribed: Boolean) {
        val name = PushTopics.name(topic, BuildConfig.DEBUG)
        val task = if (subscribed) subscribeToTopic(name) else unsubscribeFromTopic(name)
        task.addOnFailureListener { Log.w(TAG, "topic $name (subscribed=$subscribed) failed", it) }
    }

    private companion object {
        const val TAG = "PushSubscriptions"
    }
}
