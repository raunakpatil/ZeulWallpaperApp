package com.wallpaperapp.app.data.upload

import com.wallpaperapp.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** What the user fills in on the upload screen. */
data class UploadForm(val title: String, val categoryId: String, val tags: List<String>, val rightsConfirmed: Boolean)

data class UploadQuota(val enabled: Boolean, val dailyLimit: Int, val remaining: Int)

data class UploadResult(val id: String, val storageKey: String)

/** One of the user's own uploads (`my_uploads` RPC); [isLive] is false once reports hid it. */
@Serializable
data class MyUpload(
    val id: String,
    val title: String,
    @SerialName("storage_key") val storageKey: String,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    @SerialName("dominant_color") val dominantColor: String? = null,
    @SerialName("is_live") val isLive: Boolean = true,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("fav_count") val favCount: Int = 0,
)

/** Server said no (limit reached, banned, bad file…): [message] is ready to show. */
class UploadRejected(message: String) : Exception(message)

/**
 * User uploads (Raunak, 2026-10-11): the phone makes the CDN files ([UploadProcessor]) and the
 * `user-upload` edge function checks, stores and publishes them under the signed-in account.
 */
@Singleton
class UploadRepository @Inject constructor(private val supabase: Provider<SupabaseClient>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 120_000; socketTimeoutMillis = 120_000 }
            expectSuccess = false
        }
    }
    private val endpoint get() = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/user-upload"

    suspend fun quota(): UploadQuota {
        val token = token()
        val res = http.get(endpoint) { auth(token) }
        val body = res.ensureOk()
        val o = json.parseToJsonElement(body).jsonObject
        return UploadQuota(
            enabled = o["enabled"]?.jsonPrimitive?.content != "false",
            dailyLimit = o["daily_limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            remaining = o["remaining"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        )
    }

    suspend fun upload(p: ProcessedUpload, form: UploadForm): UploadResult {
        val token = token()
        val res = http.submitFormWithBinaryData(
            url = endpoint,
            formData = formData {
                append("thumb", p.thumb, fileHeaders("thumb.webp", "image/webp"))
                append("preview", p.preview, fileHeaders("preview.webp", "image/webp"))
                append("full", p.full, fileHeaders("full.webp", "image/webp"))
                p.original?.let { append("original", it, fileHeaders("original.jpg", "image/jpeg")) }
                append("title", form.title)
                append("category_id", form.categoryId)
                append("tags", form.tags.joinToString(","))
                append("device_type", p.deviceType)
                append("width", p.width.toString())
                append("height", p.height.toString())
                append("blurhash", p.blurhash)
                append("dominant_color", p.dominantColor)
                append("is_amoled", p.isAmoled.toString())
                append("rights", if (form.rightsConfirmed) "yes" else "no")
            },
        ) { auth(token) }
        val o = json.parseToJsonElement(res.ensureOk()).jsonObject
        return UploadResult(o.getValue("id").jsonPrimitive.content, o.getValue("storage_key").jsonPrimitive.content)
    }

    suspend fun myUploads(): List<MyUpload> =
        supabase.get().postgrest.rpc("my_uploads").decodeList<MyUpload>()

    suspend fun delete(id: String) {
        val token = token()
        http.delete("$endpoint?id=$id") { auth(token) }.ensureOk()
    }

    private suspend fun token(): String =
        supabase.get().auth.currentAccessTokenOrNull() ?: throw UploadRejected("Sign in to upload")

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
    }

    private fun fileHeaders(name: String, type: String) = Headers.build {
        append(HttpHeaders.ContentType, type)
        append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
    }

    private suspend fun HttpResponse.ensureOk(): String {
        val body = bodyAsText()
        if (status.isSuccess()) return body
        val message = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        throw UploadRejected(message ?: "Upload failed (${status.value}). Please try again.")
    }
}
