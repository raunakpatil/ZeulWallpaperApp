package com.wallpaperapp.app.ui.theme

import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft UI tokens. Light mode is neumorphic: [base] for every surface, lifted by a [highlight] shadow
 * up-left and a [shade] shadow down-right. Dark mode does the same on near-black, plus what makes depth
 * read on dark: a top-lit gradient on raised surfaces and a thin light rim on their upper-left edge.
 */
@Immutable
data class SoftColors(
    val isDark: Boolean,
    val base: Color,
    val raised: Color,       // card/chip fill
    val highlight: Color,    // light shadow (up-left)
    val shade: Color,        // dark shadow (down-right)
    val text: Color,
    val textMuted: Color,
    val wordmark: Color,
    val accent: Color,
    val onAccent: Color,
)

val SoftLight = SoftColors(
    isDark = false,
    base = Color(0xFFECEDF0),
    raised = Color(0xFFECEDF0),
    highlight = Color(0xFFFFFFFF),
    shade = Color(0xFFB8BEC8),
    text = Color(0xFF26292E),
    textMuted = Color(0xFF686C73),   // 4.6:1 on base (was 3:1)
    wordmark = Color(0xFF4A4E55),
    accent = ZeulOrange,
    onAccent = Color.White,
)

val SoftDark = SoftColors(
    isDark = true,
    base = Color(0xFF111113),
    raised = Color(0xFF1D1D20),
    highlight = Color(0x14FFFFFF),
    shade = Color(0xFF000000),
    text = Color(0xFFF5F5F5),
    textMuted = Color(0xFF8E8E93),
    wordmark = Color(0xFFF5F5F5),
    accent = ZeulOrangeDark,
    onAccent = Color.White,
)

val LocalSoft = staticCompositionLocalOf { SoftLight }

/** A lifted surface: paired soft shadows + the surface fill, clipped to [shape]. */
@Composable
fun Modifier.softRaised(shape: Shape, depth: Dp = 6.dp, fill: Color? = null, pressed: Float = 0f): Modifier {
    val s = LocalSoft.current
    val d = depth.value * (1f - 0.65f * pressed) // a pressed soft button sits closer to the surface
    return this
        .drawBehind {
            val blur = (d * 2f).dp.toPx()
            val off = (d * 0.7f).dp.toPx()
            if (s.isDark) {
                // Deep black shadow down-right + a faint glow up-left (hand-drawn, so no ghost copies).
                softShadow(shape, s.shade, alpha = 1f, offset = Offset(off * 1.1f, off * 1.3f), blur = blur * 1.3f)
                softShadow(shape, s.highlight, alpha = 1f, offset = Offset(-off * 0.8f, -off * 0.8f), blur = blur)
            } else {
                softShadow(shape, s.shade, alpha = 0.55f, offset = Offset(off, off), blur = blur)
                softShadow(shape, s.highlight, alpha = 1f, offset = Offset(-off, -off), blur = blur)
            }
        }
        .clip(shape)
        .background(fill ?: s.raised)
        .then(if (s.isDark) Modifier.darkConvex(shape, fill != null, pressed) else Modifier)
}

/** Dark-mode depth on a raised surface: light from the top-left (gradient sheen + a thin rim on the lit edge). */
private fun Modifier.darkConvex(shape: Shape, coloured: Boolean, pressed: Float): Modifier = drawWithCache {
    val lift = 1f - 0.6f * pressed
    val end = Offset(size.width * 0.3f, size.height) // light from above, a little to the left
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = (if (coloured) 0.16f else 0.06f) * lift),
        0.55f to Color.Transparent,
        1f to Color.Black.copy(alpha = (if (coloured) 0.16f else 0.26f) * lift),
        start = Offset.Zero,
        end = end,
    )
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = (if (coloured) 0.34f else 0.15f) * lift),
        0.4f to Color.White.copy(alpha = 0.025f),
        1f to Color.Transparent,
        start = Offset.Zero,
        end = end,
    )
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(2.dp.toPx()) // half of it is clipped away: a 1dp rim
    onDrawBehind {
        drawRect(sheen)
        drawOutline(outline, rim, style = stroke)
    }
}

/** A pressed-in surface (search field, selected chip): inner shadows instead of outer ones. */
@Composable
fun Modifier.softInset(shape: Shape, depth: Dp = 4.dp, fill: Color? = null): Modifier {
    val s = LocalSoft.current
    val d = depth.value
    return this
        .clip(shape)
        .background(fill ?: if (s.isDark) Color(0xFF17171A) else s.base)
        .drawBehind {
            val blur = (d * (if (s.isDark) 2.4f else 1.6f)).dp.toPx()
            val off = (d * (if (s.isDark) 0.8f else 0.6f)).dp.toPx()
            softInnerShadow(shape, s.shade, alpha = if (s.isDark) 1.6f else 0.6f, offset = Offset(off, off), blur = blur)
            // The lower-right inner wall catches the light, so the well reads as carved in.
            softInnerShadow(shape, if (s.isDark) Color.White.copy(alpha = 0.10f) else s.highlight, alpha = 1f, offset = Offset(-off, -off), blur = blur)
        }
        .then(if (s.isDark) Modifier.darkConcaveLip(shape) else Modifier)
}

/** Dark-mode inset: a thin lit lip on the lower edge of the cut (the mirror of [darkConvex]'s top rim). */
private fun Modifier.darkConcaveLip(shape: Shape): Modifier = drawWithCache {
    val lip = Brush.linearGradient(
        0f to Color.Black.copy(alpha = 0.5f),
        0.5f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.16f),
        start = Offset.Zero,
        end = Offset(size.width * 0.3f, size.height),
    )
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(2.dp.toPx())
    onDrawBehind { drawOutline(outline, lip, style = stroke) }
}

/*
 * Soft shadows drawn by hand: a stack of progressively larger, fainter copies of the shape. This looks
 * like a gaussian blur on every device, unlike blur effects that some GPUs/drivers don't render
 * (they draw a hard-edged copy instead).
 */
private const val SHADOW_STEPS = 9

private fun DrawScope.softShadow(shape: Shape, color: Color, alpha: Float, offset: Offset, blur: Float) {
    if (alpha <= 0f || color.alpha <= 0f) return
    for (i in SHADOW_STEPS downTo 1) {
        val f = i / SHADOW_STEPS.toFloat()           // 1 = outermost, faintest ring
        val grow = blur * f
        val outline = shape.createOutline(Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
        val layerAlpha = alpha * (1f - f + 1f / SHADOW_STEPS) * (2f / SHADOW_STEPS)
        translate(offset.x - grow, offset.y - grow) {
            drawOutline(outline, color.copy(alpha = (color.alpha * layerAlpha).coerceIn(0f, 1f)))
        }
    }
}

/** Inner shadow: everything inside the shape except an offset, shrinking copy of it, in fading layers. */
private fun DrawScope.softInnerShadow(shape: Shape, color: Color, alpha: Float, offset: Offset, blur: Float) {
    if (alpha <= 0f || color.alpha <= 0f) return
    val outer = Path().apply { addRect(Rect(-size.width, -size.height, size.width * 2, size.height * 2)) }
    for (i in 1..SHADOW_STEPS) {
        val f = i / SHADOW_STEPS.toFloat()
        val shrink = blur * f
        val holeSize = Size((size.width - 2 * shrink).coerceAtLeast(1f), (size.height - 2 * shrink).coerceAtLeast(1f))
        val hole = Path().apply {
            addOutline(shape.createOutline(holeSize, layoutDirection, this@softInnerShadow))
            translate(Offset(offset.x + shrink, offset.y + shrink))
        }
        val ring = Path().apply { op(outer, hole, PathOperation.Difference) }
        drawPath(ring, color.copy(alpha = (color.alpha * alpha * (1.4f / SHADOW_STEPS) * (1f - f * 0.6f)).coerceIn(0f, 1f)))
    }
}

/** Square soft button with a single icon (header actions, tile actions). */
@Composable
fun SoftIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    tint: Color = LocalSoft.current.wordmark,
) {
    val shape = RoundedCornerShape(ZeulRadius.Control)
    val press = rememberSoftPress()
    Box(
        modifier
            .size(size)
            .pressScale(press, 0.06f)
            .softRaised(shape, depth = 5.dp, pressed = press.progress)
            .softClickable(press, contentDescription, haptic = HapticFeedbackType.ContextClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Pressed-in pill that looks like a search field and opens the real search screen. */
@Composable
fun SoftSearchBar(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSoft.current
    val shape = RoundedCornerShape(ZeulRadius.Control)
    val press = rememberSoftPress()
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(press, 0.015f)
            .softInset(shape, depth = (4f + 2f * press.progress).dp)
            .softClickable(press, placeholder, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = s.textMuted, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(ZeulIcons.Search, contentDescription = null, tint = s.textMuted, modifier = Modifier.size(22.dp))
    }
}

/** Small raised pill button with an icon (tile heart/download). [active] fills it with the accent. */
@Composable
fun SoftPillButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    haptic: HapticFeedbackType? = null,
) {
    val s = LocalSoft.current
    val shape = RoundedCornerShape(ZeulRadius.Control)
    val press = rememberSoftPress()
    val tint by animateColorAsState(if (active) s.accent else s.textMuted, ZeulMotion.snappy(), label = "pillTint")
    Box(
        modifier
            .height(36.dp)
            .pressScale(press, 0.06f)
            .softRaised(shape, depth = 3.dp, pressed = press.progress)
            .softClickable(press, contentDescription, haptic = haptic, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(19.dp).popOnActivate(active))
    }
}

/** Accent dot used under the active item on wide-screen rails. */
@Composable
fun AccentDot(modifier: Modifier = Modifier) {
    Box(modifier.size(5.dp).clip(CircleShape).background(LocalSoft.current.accent))
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/**
 * Screen title in the Zeul display face. Home's "ZEUL" wordmark and every top-level screen title use
 * this, so they share font, size and position; [trailing] adds a header action (Home's gem).
 */
@Composable
fun SoftScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    accentFirstLetter: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val s = LocalSoft.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 22.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // The wordmark's first letter in Zeul orange (Home's "ZEUL").
            if (accentFirstLetter && text.isNotEmpty()) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = s.accent)) { append(text.take(1)) }
                    append(text.drop(1))
                }
            } else AnnotatedString(text),
            style = MaterialTheme.typography.displaySmall,
            fontSize = 38.sp,
            fontWeight = if (s.isDark) FontWeight.Normal else FontWeight.Light,
            letterSpacing = 1.2.sp,
            color = s.wordmark,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * A raised card that groups list rows (settings). Inside it, Material surfaces are transparent so
 * ListItem rows sit on the card instead of painting their own background.
 */
@Composable
fun SoftGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .softRaised(RoundedCornerShape(ZeulRadius.Panel), depth = 6.dp)
            .padding(vertical = 6.dp),
    ) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent)) {
            Column(content = content)
        }
    }
}
