package com.wallpaperapp.app.ui.components

import com.wallpaperapp.app.ui.theme.ZeulRadius
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wallpaperapp.app.ui.navigation.TopLevelDestination
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.ZeulMotion
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.theme.softInset
import com.wallpaperapp.app.ui.theme.softRaised

/**
 * Floating soft pill with icon-only tabs (labels are read by TalkBack). One indicator glides between
 * tabs on a spring: a pressed-in dip in light mode, a filled accent circle in dark mode.
 */
@Composable
fun SoftNavBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalSoft.current
    val tabs = TopLevelDestination.entries
    val selectedIndex = tabs.indexOfFirst(isSelected)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .softRaised(RoundedCornerShape(ZeulRadius.Panel), depth = 8.dp)
                .padding(horizontal = 10.dp),
        ) {
            val slot = maxWidth / tabs.size
            val indicatorX by animateDpAsState(slot * selectedIndex.coerceAtLeast(0) + (slot - INDICATOR) / 2, ZeulMotion.smooth(), label = "tabIndicator")
            val indicatorAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "tabIndicatorAlpha")
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .size(INDICATOR)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .then(if (s.isDark) Modifier.softRaised(RoundedCornerShape(ZeulRadius.Control), depth = 4.dp, fill = s.accent) else Modifier.softInset(RoundedCornerShape(ZeulRadius.Control), depth = 3.dp)),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, dest ->
                    val selected = index == selectedIndex
                    val label = stringResource(dest.label)
                    val press = rememberSoftPress()
                    val tint by animateColorAsState(
                        when {
                            selected && s.isDark -> s.onAccent
                            selected -> s.accent
                            else -> s.textMuted
                        },
                        ZeulMotion.snappy(),
                        label = "tabTint",
                    )
                    val lift by animateFloatAsState(if (selected) 1f else 0f, ZeulMotion.smooth(), label = "tabLift")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .softClickable(press, label, role = Role.Tab, haptic = if (selected) null else HapticFeedbackType.SegmentTick) { onSelect(dest) }
                            .semantics { this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier
                                .size(24.dp)
                                .pressScale(press, 0.12f)
                                .graphicsLayer { val k = 1f + 0.06f * lift; scaleX = k; scaleY = k },
                        )
                    }
                }
            }
        }
    }
}

private val INDICATOR = 48.dp
