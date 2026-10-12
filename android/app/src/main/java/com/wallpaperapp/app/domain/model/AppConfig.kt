package com.wallpaperapp.app.domain.model

/** Typed view of the `app_config` table (CONTRACTS section 4). Defaults match the contract. */
data class AppConfig(
    /** Empty until the first fetch; never hard-coded (CONTRACTS section 3). */
    val cdnBaseUrl: String = "",
    val minAppVersion: Int = 1,
    val maintenance: Maintenance = Maintenance(),
    val ads: Ads = Ads(),
    val unlockMode: UnlockMode = UnlockMode.HOURS_24,
    val freeMaxResolution: Int = 2560,
    val homeSections: List<String> = listOf("for_you", "curated", "popular", "new", "amoled", "tablet", "premium"),
    val featuredBanner: FeaturedBanner? = null,
    val dailyWallpaperId: String? = null,
    val paywall: Paywall = Paywall(),
    /** v1.6 (B7): show the email-code sign-in option. Off until custom SMTP exists (A17). */
    val authEmailEnabled: Boolean = false,
) {
    data class Maintenance(val enabled: Boolean = false, val message: String = "")
    data class Ads(val nativeEvery: Int = 12, val appOpenEnabled: Boolean = false, val appOpenMinHours: Int = 4)
    data class FeaturedBanner(val wallpaperId: String?, val collectionId: String?, val title: String)
    data class Paywall(val highlight: String = "annual", val headline: String = "")
    enum class UnlockMode { PERMANENT, HOURS_24 }
}
