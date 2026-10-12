package com.wallpaperapp.app.data.image

import android.content.ContentValues
import androidx.annotation.RequiresApi
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Saves images to Pictures/Wallpapers so they show up in the gallery. */
@Singleton
class GallerySaver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun save(bytes: ByteArray, displayName: String, mimeType: String): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(bytes, displayName, mimeType)
        else saveLegacy(bytes, displayName, mimeType)
    }

    // Android 10+: no permission needed; the file stays hidden until fully written.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(bytes: ByteArray, displayName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create gallery entry")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Couldn't open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    // Android 8–9: needs WRITE_EXTERNAL_STORAGE (requested by the UI before calling).
    @Suppress("DEPRECATION")
    private fun saveLegacy(bytes: ByteArray, displayName: String, mimeType: String): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Couldn't create $dir")
        val file = File(dir, displayName)
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(file)
    }

    companion object {
        const val FOLDER = "Zeul"
    }
}
