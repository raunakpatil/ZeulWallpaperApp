package com.wallpaperapp.app.data.remote

import com.wallpaperapp.app.domain.model.AppConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A row of the `app_config` table. */
@Serializable
data class AppConfigRow(val key: String, val value: JsonElement)

/**
 * Turns raw `app_config` rows into [AppConfig]. Unknown keys are ignored and a missing or
 * malformed value falls back to the contract default, so a bad dashboard edit can't crash the app.
 */
object AppConfigParser {

    fun parse(values: Map<String, JsonElement>): AppConfig {
        val d = AppConfig()
        return AppConfig(
            cdnBaseUrl = values.string("cdn_base_url")?.trimEnd('/') ?: d.cdnBaseUrl,
            minAppVersion = values.int("min_app_version") ?: d.minAppVersion,
            maintenance = values.obj("maintenance")?.let {
                AppConfig.Maintenance(
                    enabled = it.bool("enabled") ?: false,
                    message = it.string("message").orEmpty(),
                )
            } ?: d.maintenance,
            ads = values.obj("ads")?.let {
                AppConfig.Ads(
                    nativeEvery = it.int("native_every") ?: d.ads.nativeEvery,
                    appOpenEnabled = it.bool("app_open_enabled") ?: d.ads.appOpenEnabled,
                    appOpenMinHours = it.int("app_open_min_hours") ?: d.ads.appOpenMinHours,
                )
            } ?: d.ads,
            unlockMode = when (values.string("unlock_mode")) {
                "24h" -> AppConfig.UnlockMode.HOURS_24
                "permanent" -> AppConfig.UnlockMode.PERMANENT
                else -> d.unlockMode
            },
            freeMaxResolution = values.int("free_max_resolution") ?: d.freeMaxResolution,
            homeSections = runCatching {
                values["home_sections"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: d.homeSections,
            featuredBanner = values.obj("featured_banner")?.let {
                val title = it.string("title") ?: return@let null
                AppConfig.FeaturedBanner(
                    wallpaperId = it.string("wallpaper_id"),
                    collectionId = it.string("collection_id"),
                    title = title,
                )
            },
            dailyWallpaperId = values.string("daily_wallpaper_id"),
            paywall = values.obj("paywall")?.let {
                AppConfig.Paywall(
                    highlight = it.string("highlight") ?: d.paywall.highlight,
                    headline = it.string("headline") ?: d.paywall.headline,
                )
            } ?: d.paywall,
            authEmailEnabled = values.bool("auth_email_enabled") ?: d.authEmailEnabled,
        )
    }

    private fun Map<String, JsonElement>.primitive(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun Map<String, JsonElement>.string(key: String) =
        primitive(key)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun Map<String, JsonElement>.int(key: String) = primitive(key)?.intOrNull
    private fun Map<String, JsonElement>.bool(key: String) = primitive(key)?.booleanOrNull
    private fun Map<String, JsonElement>.obj(key: String): JsonObject? =
        runCatching { this[key]?.takeUnless { it is JsonNull }?.jsonObject }.getOrNull()
}
