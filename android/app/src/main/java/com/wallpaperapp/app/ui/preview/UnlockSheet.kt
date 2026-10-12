package com.wallpaperapp.app.ui.preview

import com.wallpaperapp.app.ui.theme.softSheetColor
import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.SoftButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** "Watch a short ad or go Premium". Rewarded ads are always opt-in: nothing plays until the user taps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockSheet(
    sheet: UnlockSheet,
    onWatchAd: () -> Unit,
    onGoPremium: () -> Unit,
    onDismiss: () -> Unit,
) {
    val soft = LocalSoft.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = softSheetColor()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(64.dp).softRaised(CircleShape, depth = 5.dp), contentAlignment = Alignment.Center) {
                Icon(if (sheet.canWatchAd) ZeulIcons.Lock else ZeulIcons.Gem, null, tint = soft.accent, modifier = Modifier.size(28.dp))
            }
            Text(
                if (sheet.canWatchAd) stringResource(R.string.unlock_title) else stringResource(R.string.unlock_title_premium),
                fontFamily = ZeulDisplay,
                fontSize = 24.sp,
                fontWeight = if (soft.isDark) FontWeight.Normal else FontWeight.Light,
                color = soft.wordmark,
                textAlign = TextAlign.Center,
            )
            Text(
                if (sheet.canWatchAd) {
                    stringResource(
                        when {
                            sheet.unlockFor24h && sheet.action == GatedAction.DOWNLOAD -> R.string.unlock_body_ad_download_24h
                            sheet.unlockFor24h -> R.string.unlock_body_ad_apply_24h
                            sheet.action == GatedAction.DOWNLOAD -> R.string.unlock_body_ad_download
                            else -> R.string.unlock_body_ad_apply
                        },
                    )
                } else {
                    stringResource(if (sheet.action == GatedAction.DOWNLOAD) R.string.unlock_body_premium_download else R.string.unlock_body_premium_apply)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = soft.textMuted,
                textAlign = TextAlign.Center,
            )
            if (sheet.canWatchAd) {
                AccentButton(
                    text = if (sheet.adLoading) stringResource(R.string.loading_ad) else stringResource(R.string.watch_ad),
                    onClick = onWatchAd,
                    enabled = !sheet.adLoading,
                    loading = sheet.adLoading,
                    leading = ZeulIcons.Play,
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftButton(stringResource(R.string.go_premium_trial), onGoPremium, Modifier.fillMaxWidth(), leading = ZeulIcons.Gem, leadingTint = soft.accent)
            } else {
                AccentButton(stringResource(R.string.go_premium_trial), onGoPremium, Modifier.fillMaxWidth(), leading = ZeulIcons.Gem)
            }
            Text(stringResource(R.string.premium_perks_short), style = MaterialTheme.typography.labelMedium, color = soft.textMuted, textAlign = TextAlign.Center)
        }
    }
}
