package com.wallpaperapp.app.domain.model

/**
 * `{cdn_base_url}/{storage_key}/{variant}` (CONTRACTS section 3). Returns null until the CDN base
 * URL has been loaded from `app_config`. Files are immutable, so these URLs can be cached forever.
 */
fun Wallpaper.imageUrl(cdnBaseUrl: String, variant: ImageVariant): String? {
    if (cdnBaseUrl.isBlank()) return null
    val file = when (variant) {
        ImageVariant.THUMB -> "thumb.webp"
        ImageVariant.PREVIEW -> "preview.webp"
        ImageVariant.FULL -> "full.webp"
        ImageVariant.ORIGINAL -> "original.$originalExt"
    }
    return "${cdnBaseUrl.trimEnd('/')}/${storageKey.trim('/')}/$file"
}
