package com.wallpaperapp.app.ui.favorites

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallpaperapp.app.R
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.AutoChangerSettings
import com.wallpaperapp.app.ui.theme.AccentButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.SoftSectionLabel
import com.wallpaperapp.app.ui.theme.SoftSegmented
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulMotion
import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.theme.softInset
import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.softSheetColor

/** What the Favourites tab's auto-change row shows. */
data class FavAutoChange(
    /** On, with favourites as the source (another source set in Profile reads as off here). */
    val on: Boolean = false,
    val premium: Boolean = false,
    val favouriteCount: Int = 0,
    val intervalHours: Int = 24,
    val target: ApplyTarget = ApplyTarget.HOME,
) {
    val enoughFavourites: Boolean get() = favouriteCount >= MIN_FAVOURITES

    companion object {
        /** Rotating one wallpaper isn't rotating. */
        const val MIN_FAVOURITES = 2
    }
}

/**
 * The auto-change row at the top of Favourites: a shuffle tile, title + status, and a switch.
 * The switch turns it on/off; the row opens [AutoChangeSheet] for interval, screen and "Change now".
 * Not Premium: the switch opens the paywall instead.
 */
@Composable
fun AutoChangeRow(
    state: FavAutoChange,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onGoPremium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    val shape = RoundedCornerShape(ZeulRadius.Panel)
    val enabled = !state.premium || state.enoughFavourites
    val status = when {
        !state.premium -> stringResource(R.string.fav_autochange_premium)
        !state.enoughFavourites -> stringResource(R.string.fav_autochange_need, FavAutoChange.MIN_FAVOURITES)
        state.on -> stringResource(R.string.fav_autochange_on, intervalLabel(state.intervalHours), targetLabel(state.target))
        else -> stringResource(R.string.fav_autochange_off)
    }
    val tileFill by animateColorAsState(if (state.on) soft.accent else soft.raised, ZeulMotion.snappy(), label = "shuffleTile")
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(press, 0.015f)
            .softRaised(shape, depth = 5.dp, pressed = press.progress)
            .softClickable(press, stringResource(R.string.fav_autochange_settings), onClick = if (state.premium) onOpenSettings else onGoPremium)
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .then(
                    if (state.on) Modifier.softRaised(RoundedCornerShape(ZeulRadius.Control), depth = 3.dp, fill = tileFill)
                    else Modifier.softInset(RoundedCornerShape(ZeulRadius.Control), depth = 3.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.premium) ZeulIcons.Shuffle else ZeulIcons.Gem,
                contentDescription = null,
                tint = if (state.on) soft.onAccent else soft.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
            Text(stringResource(R.string.fav_autochange_title), style = MaterialTheme.typography.titleMedium, color = soft.text)
            Text(status, style = MaterialTheme.typography.bodySmall, color = soft.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val stateText = if (state.on) "on" else "off"
        Switch(
            checked = state.on,
            onCheckedChange = { on -> if (state.premium) onToggle(on) else onGoPremium() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = soft.onAccent,
                checkedTrackColor = soft.accent,
                checkedBorderColor = soft.accent,
                uncheckedThumbColor = soft.textMuted,
                uncheckedTrackColor = soft.base,
                uncheckedBorderColor = soft.textMuted.copy(alpha = 0.5f),
            ),
            modifier = Modifier.semantics { stateDescription = stateText },
        )
    }
}

/** Interval, screen and "Change now", in a sheet over the Favourites tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoChangeSheet(
    state: FavAutoChange,
    onInterval: (Int) -> Unit,
    onTarget: (ApplyTarget) -> Unit,
    onChangeNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val soft = LocalSoft.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = softSheetColor()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.fav_autochange_sheet_title), style = MaterialTheme.typography.headlineSmall, color = soft.text)
            Text(stringResource(R.string.fav_autochange_sheet_body), style = MaterialTheme.typography.bodyMedium, color = soft.textMuted)
            SoftSectionLabel(stringResource(R.string.auto_changer_every), Modifier.padding(top = 10.dp))
            SoftSegmented(
                options = AutoChangerSettings.INTERVALS.map { intervalLabel(it) },
                selectedIndex = AutoChangerSettings.INTERVALS.indexOf(state.intervalHours),
                onSelect = { onInterval(AutoChangerSettings.INTERVALS[it]) },
            )
            SoftSectionLabel(stringResource(R.string.auto_changer_set_on), Modifier.padding(top = 6.dp))
            val targets = listOf(ApplyTarget.HOME, ApplyTarget.LOCK, ApplyTarget.BOTH)
            SoftSegmented(
                options = listOf(stringResource(R.string.target_home), stringResource(R.string.target_lock), stringResource(R.string.target_both)),
                selectedIndex = targets.indexOf(state.target),
                onSelect = { onTarget(targets[it]) },
            )
            AccentButton(
                stringResource(R.string.change_now),
                onChangeNow,
                Modifier.fillMaxWidth().padding(top = 14.dp),
                enabled = state.on && state.enoughFavourites,
                leading = ZeulIcons.Shuffle,
            )
        }
    }
}

@Composable
private fun intervalLabel(hours: Int): String =
    if (hours == 24) stringResource(R.string.interval_day_lower) else stringResource(R.string.interval_hours, hours)

@Composable
private fun targetLabel(target: ApplyTarget): String = stringResource(
    when (target) {
        ApplyTarget.HOME -> R.string.target_home_screen
        ApplyTarget.LOCK -> R.string.target_lock_screen
        ApplyTarget.BOTH -> R.string.target_both_screens
    },
)
