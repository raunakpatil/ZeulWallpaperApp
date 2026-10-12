package com.wallpaperapp.app.data.image

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Non-2xx response; [code] lets callers react to e.g. a missing optional file (404). */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

@Singleton
class ImageFetcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Raw file bytes, so downloads are saved exactly as uploaded (no re-encoding). */
    suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            if (connection.responseCode !in 200..299) throw HttpStatusException(connection.responseCode, url)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Software bitmap (editable) no larger than [maxLongEdge], through Coil so the preview's
     * cached files are reused.
     */
    suspend fun loadBitmap(url: String, maxLongEdge: Int): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(maxLongEdge)
            .allowHardware(false)
            .build()
        return when (val result = SingletonImageLoader.get(context).execute(request)) {
            is SuccessResult -> result.image.toBitmap()
            is ErrorResult -> throw IOException("Couldn't load image", result.throwable)
        }
    }
}
