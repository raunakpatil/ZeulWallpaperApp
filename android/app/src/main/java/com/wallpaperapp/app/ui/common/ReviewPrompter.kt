package com.wallpaperapp.app.ui.common

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.wallpaperapp.app.data.local.UserPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** When to ask for a Play review: after a few happy moments, at most once a month. */
object ReviewPolicy {
    const val SUCCESSES_BEFORE_ASKING = 3
    val MIN_GAP_MS: Long = TimeUnit.DAYS.toMillis(30)

    fun shouldAsk(successes: Int, lastAskedAt: Long?, now: Long): Boolean =
        successes >= SUCCESSES_BEFORE_ASKING && (lastAskedAt == null || now - lastAskedAt >= MIN_GAP_MS)
}

/**
 * Google Play In-App Review. Play decides whether the dialog really shows (quota), and it never
 * tells us the outcome, so we just ask at good moments (a wallpaper was set or saved).
 */
@Singleton
class ReviewPrompter @Inject constructor(private val preferences: UserPreferences) {

    suspend fun onSuccess(activity: Activity) {
        val (successes, lastAskedAt) = preferences.recordSuccessForReview()
        val now = System.currentTimeMillis()
        if (!ReviewPolicy.shouldAsk(successes, lastAskedAt, now)) return
        preferences.markReviewAsked(now)
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.launchReview(activity, manager.requestReview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReviewPrompter", "review flow unavailable", e) // e.g. not installed from Play
        }
    }
}
