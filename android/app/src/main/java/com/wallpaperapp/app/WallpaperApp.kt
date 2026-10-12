package com.wallpaperapp.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import com.wallpaperapp.app.push.PushNotifications
import com.wallpaperapp.app.push.PushSubscriptions
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.wallpaperapp.app.work.AutoChangerScheduler

@HiltAndroidApp
class WallpaperApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var pushSubscriptions: PushSubscriptions
    @Inject lateinit var autoChanger: AutoChangerScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager starts on demand with Hilt-built workers (the default initializer is removed in the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        PushNotifications.createChannels(this)
        pushSubscriptions.start()
        autoChanger.start()
    }

    // CDN files are immutable (CONTRACTS section 3), so a large disk cache is safe.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
