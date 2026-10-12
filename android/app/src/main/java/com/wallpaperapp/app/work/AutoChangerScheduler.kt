package com.wallpaperapp.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.di.ApplicationScope
import com.wallpaperapp.app.domain.model.AutoChangerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the periodic auto-changer job in line with its settings and the premium status. */
@Singleton
class AutoChangerScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferences,
    private val premiumManager: PremiumManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun start() {
        scope.launch {
            combine(preferences.autoChanger, premiumManager.isPremium) { settings, premium -> Triple(settings.enabled, settings.intervalHours, premium) }
                .distinctUntilChanged()
                .collect { (enabled, hours, premium) ->
                    if (premium) preferences.setLastKnownPremium(true)
                    when {
                        enabled && premium -> schedule(hours)
                        // Premium ended: stop, and switch the setting off so it doesn't look on.
                        enabled && !premium && premiumKnownLapsed() -> {
                            preferences.setLastKnownPremium(false)
                            workManager.cancelUniqueWork(PERIODIC)
                            preferences.updateAutoChanger { it.copy(enabled = false) }
                        }
                        !enabled -> workManager.cancelUniqueWork(PERIODIC)
                    }
                }
        }
    }

    /** "Change now" from settings. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<AutoChangerWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedule(hours: Int) {
        val safe = hours.takeIf { it in AutoChangerSettings.INTERVALS } ?: 24
        val request = PeriodicWorkRequestBuilder<AutoChangerWorker>(safe.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(safe.toLong(), TimeUnit.HOURS)
            .build()
        // UPDATE keeps the current schedule's timing when only the settings change.
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * isPremium starts false until billing answers, so a false right after launch isn't proof the
     * subscription ended. Treat it as lapsed only when billing has had time to respond.
     */
    private suspend fun premiumKnownLapsed(): Boolean {
        delay(PREMIUM_SETTLE_MS)
        return !premiumManager.isPremium.value
    }

    private companion object {
        const val PERIODIC = "auto_changer"
        const val ONE_OFF = "auto_changer_now"
        const val PREMIUM_SETTLE_MS = 15_000L
    }
}
