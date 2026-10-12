package com.wallpaperapp.app.ads

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.wallpaperapp.app.R

/**
 * A native ad styled like a wallpaper grid tile: media on top, headline, call-to-action,
 * and the "Ad" badge that AdMob policy requires.
 */
@Composable
internal fun NativeAdTile(ad: NativeAd, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceVariant.toArgb()
    val onSurface = colors.onSurfaceVariant.toArgb()
    val primary = colors.primary.toArgb()
    val onPrimary = colors.onPrimary.toArgb()
    val badgeBg = colors.tertiaryContainer.toArgb()
    val badgeFg = colors.onTertiaryContainer.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

            val media = MediaView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
                minimumHeight = dp(120) // policy: media view must be at least 120dp
                setImageScaleType(ImageView.ScaleType.CENTER_CROP) // any aspect ratio fills the tile
            }
            val headline = TextView(ctx).apply {
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(8), dp(6), dp(8), 0)
            }
            val cta = Button(ctx).apply {
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(onPrimary)
                backgroundTintList = ColorStateList.valueOf(primary)
                minHeight = dp(36)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    setMargins(dp(8), dp(4), dp(8), dp(8))
                }
            }
            val column = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(media)
                addView(headline, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(cta)
            }
            val badge = TextView(ctx).apply {
                text = ctx.getString(R.string.ad_badge)
                setTextColor(badgeFg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(badgeBg) }
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }
            NativeAdView(ctx).apply {
                setBackgroundColor(surface)
                val frame = FrameLayout(ctx)
                frame.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                frame.addView(badge, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                    setMargins(dp(6), dp(6), 0, 0)
                })
                addView(frame, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                mediaView = media
                headlineView = headline
                callToActionView = cta
            }
        },
        update = { view ->
            (view.headlineView as TextView).text = ad.headline
            (view.callToActionView as Button).apply {
                text = ad.callToAction ?: "Open"
                visibility = if (ad.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE
            }
            view.mediaView?.mediaContent = ad.mediaContent
            view.setNativeAd(ad)
        },
    )
}

/** Shown while a native ad is loading, or when none is available (keeps the grid layout stable). */
@Composable
internal fun NativeAdPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.ad_sponsored), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

