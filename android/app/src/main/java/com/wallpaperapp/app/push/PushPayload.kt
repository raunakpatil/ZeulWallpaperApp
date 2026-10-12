package com.wallpaperapp.app.push

import com.wallpaperapp.app.domain.model.AppLink
import com.wallpaperapp.app.R
import androidx.annotation.StringRes
import com.wallpaperapp.app.domain.model.ShareLinks

/** Android notification channels (CONTRACTS §8). Users can mute each in system settings. */
enum class PushChannel(val id: String, @param:StringRes val label: Int, @param:StringRes val description: Int) {
    ANNOUNCEMENTS("announcements", R.string.channel_announcements, R.string.channel_announcements_body),
    NEW_DROPS("new_drops", R.string.notif_new_drops, R.string.notif_new_drops_body),
    DAILY("daily", R.string.notif_daily, R.string.notif_daily_body),
}

/** Topic names: `{env}_{name}`, env = `debug` for debug builds so test sends never reach real users. */
object PushTopics {
    const val ALL = "all"
    const val NEW_DROPS = "new_drops"
    const val DAILY = "daily_wallpaper"

    fun name(topic: String, debug: Boolean): String = "${if (debug) "debug" else "prod"}_$topic"
}

/** A data-only FCM message (CONTRACTS §8). All values are strings; "" means absent. */
data class PushPayload(
    val channel: PushChannel,
    val title: String,
    val body: String,
    val imageUrl: String?,
    /** Validated link to open on tap; null = open Home. */
    val link: String?,
    val pushId: String?,
) {
    companion object {
        private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** Null when there's nothing worth showing (no title). Untrusted input: everything is checked. */
        fun from(data: Map<String, String>): PushPayload? {
            val title = data["title"].orEmpty().trim().take(65)
            if (title.isEmpty()) return null
            val channel = when (data["type"]) {
                "daily" -> PushChannel.DAILY
                "new_drop", "collection" -> PushChannel.NEW_DROPS
                else -> PushChannel.ANNOUNCEMENTS
            }
            return PushPayload(
                channel = channel,
                title = title,
                body = data["body"].orEmpty().trim().take(240),
                imageUrl = data["image_url"]?.takeIf { it.startsWith("https://") },
                // Only links the app understands; anything else just opens Home.
                link = data["link"]?.takeIf { ShareLinks.parse(it) != null },
                pushId = data["push_id"]?.takeIf { UUID.matches(it) },
            )
        }
    }

    val appLink: AppLink? get() = ShareLinks.parse(link)
}
