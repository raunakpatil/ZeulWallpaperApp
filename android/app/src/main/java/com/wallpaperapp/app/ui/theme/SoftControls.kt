package com.wallpaperapp.app.ui.theme

import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * More soft-UI controls (see docs/design/app-ui-soft.md): primary/secondary buttons, glass
 * buttons for use over photos, chips, a sliding segmented control, an inset text field, a top bar.
 */

/** Primary action: filled Zeul orange pill. Disabled = muted. [leading] draws before the label (icon/spinner). */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: ImageVector? = null,
    height: Dp = 54.dp,
) {
    val s = LocalSoft.current
    val press = rememberSoftPress()
    val fill by animateColorAsState(
        if (enabled) s.accent else if (s.isDark) Color(0xFF2A2A2D) else Color(0xFFD8DBE0), ZeulMotion.snappy(), label = "accentFill",
    )
    val content = if (enabled) s.onAccent else s.textMuted
    Row(
        modifier
            .height(height)
            .pressScale(press, 0.03f)
            .softRaised(RoundedCornerShape(ZeulRadius.Control), depth = if (enabled) 5.dp else 2.dp, fill = fill, pressed = press.progress)
            .then(if (enabled) Modifier.softClickable(press, text, haptic = HapticFeedbackType.ContextClick, onClick = onClick) else Modifier.disabledSemantics())
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        when {
            loading -> {
                CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            leading != null -> {
                Icon(leading, null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(text, color = content, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: raised soft pill in the surface colour. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    leadingTint: Color? = null,
    height: Dp = 54.dp,
) {
    val s = LocalSoft.current
    val press = rememberSoftPress()
    val content = if (enabled) s.text else s.textMuted.copy(alpha = 0.6f)
    Row(
        modifier
            .height(height)
            .pressScale(press, 0.03f)
            .softRaised(RoundedCornerShape(ZeulRadius.Control), depth = if (enabled) 5.dp else 2.dp, pressed = press.progress)
            .then(if (enabled) Modifier.softClickable(press, text, onClick = onClick) else Modifier.disabledSemantics())
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = leadingTint ?: content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Quiet text action (Skip, Restore, Change email…). */
@Composable
fun SoftTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color? = null) {
    val s = LocalSoft.current
    val press = rememberSoftPress()
    Box(
        modifier
            .pressScale(press, 0.05f)
            .clip(RoundedCornerShape(ZeulRadius.Control))
            .then(if (enabled) Modifier.softClickable(press, text, onClick = onClick) else Modifier.disabledSemantics())
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (enabled) (color ?: s.accent) else s.textMuted.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Round frosted button for use over photos (preview, apply editor). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 46.dp,
    haptic: HapticFeedbackType? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val press = rememberSoftPress()
    Box(
        modifier
            .size(size)
            .pressScale(press, 0.08f)
            .clip(RoundedCornerShape(ZeulRadius.Control))
            .background(GlassFill)
            .border(1.dp, GlassEdge, RoundedCornerShape(ZeulRadius.Control))
            .softClickable(press, contentDescription, haptic = haptic, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Frosted panel behind controls over a photo. */
fun Modifier.glassPanel(radius: Dp = ZeulRadius.Panel): Modifier =
    clip(RoundedCornerShape(radius)).background(GlassFill).border(1.dp, GlassEdge, RoundedCornerShape(radius))

val GlassFill = Color(0x66101012)
val GlassEdge = Color(0x24FFFFFF)

/** Selectable soft chip: raised when off, pressed in with an accent label when on. */
@Composable
fun SoftChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: ImageVector? = null) {
    val s = LocalSoft.current
    val press = rememberSoftPress()
    val shape = RoundedCornerShape(ZeulRadius.Chip)
    val color by animateColorAsState(if (selected) s.accent else s.text, ZeulMotion.snappy(), label = "chipText")
    Box(
        modifier
            .height(40.dp)
            .pressScale(press, 0.05f)
            .then(if (selected) Modifier.softInset(shape, depth = 3.dp) else Modifier.softRaised(shape, depth = 3.dp, pressed = press.progress))
            .softClickable(press, label, role = Role.Checkbox, haptic = HapticFeedbackType.SegmentTick, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            if (trailing != null) Icon(trailing, null, tint = color, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

/** Soft segmented control: a pressed-in track with a raised thumb that slides to the selected option. */
@Composable
fun SoftSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSoft.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .softInset(RoundedCornerShape(ZeulRadius.Control), depth = 3.dp)
            .padding(5.dp),
    ) {
        val slot = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(slot * selectedIndex.coerceAtLeast(0), ZeulMotion.smooth(), label = "segThumb")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(slot)
                .fillMaxHeight()
                .softRaised(RoundedCornerShape(ZeulRadius.Small), depth = 3.dp, fill = if (s.isDark) s.accent else null),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                val press = rememberSoftPress()
                val color by animateColorAsState(
                    when {
                        selected && s.isDark -> s.onAccent
                        selected -> s.accent
                        else -> s.textMuted
                    },
                    ZeulMotion.snappy(),
                    label = "segText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .softClickable(press, label, role = Role.RadioButton, haptic = if (selected) null else HapticFeedbackType.SegmentTick) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** Inset soft text field (single line). */
@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    letterSpacing: Float = 0f,
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val s = LocalSoft.current
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .softInset(RoundedCornerShape(ZeulRadius.Control), depth = 4.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = s.textMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = s.textMuted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = s.text, letterSpacing = letterSpacing.sp),
                cursorBrush = SolidColor(s.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

/** Top bar for detail screens: soft back button + optional title (and trailing actions). */
@Composable
fun SoftTopBar(onBack: () -> Unit, backLabel: String, modifier: Modifier = Modifier, title: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val s = LocalSoft.current
    Row(
        modifier.fillMaxWidth().padding(PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIconButton(ZeulIcons.Back, backLabel, onBack, size = 46.dp, tint = s.text)
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = s.wordmark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp).weight(1f).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing?.invoke()
    }
}

/** Uppercase muted section label used inside soft screens. */
@Composable
fun SoftSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.6.sp,
        color = LocalSoft.current.textMuted,
        modifier = modifier.semantics { heading() },
    )
}

/** Bottom-sheet surface colour for the soft look. */
@Composable
fun softSheetColor(): Color = LocalSoft.current.let { if (it.isDark) it.raised else it.base }

/** A disabled control must still say so to TalkBack/tests. */
private fun Modifier.disabledSemantics(): Modifier = semantics(mergeDescendants = true) { disabled() }
