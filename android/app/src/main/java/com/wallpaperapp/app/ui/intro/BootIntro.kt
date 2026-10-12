package com.wallpaperapp.app.ui.intro

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.hypot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import com.wallpaperapp.app.R
import kotlinx.coroutines.delay

/**
 * Boot intro (docs/design/boot-intro.md): the hamster video full-screen on a cold start.
 * Poster (= frame 0) shows until the first video frame renders, so there's never a black flash;
 * the last frame stays underneath at the end. Tap anywhere to skip. [onEnd] fires once, when the
 * video ends, is skipped, or fails; the caller animates the hand-over to the app.
 */
@Composable
fun BootIntro(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    var ended by remember { mutableStateOf(false) }
    var firstFrameShown by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    val end = remember { { if (!ended) { ended = true; onEnd() } } }

    // Safety nets: a video that never starts (or a device that can't decode it) must not block the app.
    LaunchedEffect(Unit) {
        delay(FIRST_FRAME_TIMEOUT_MS)
        if (!firstFrameShown) end()
        delay(MAX_DURATION_MS - FIRST_FRAME_TIMEOUT_MS)
        end()
    }

    val top = colorResource(R.color.boot_intro_background)
    val floor = colorResource(R.color.boot_intro_floor)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, floor)))
            .clearAndSetSemantics { } // decorative; TalkBack users skip the intro entirely
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = end),
    ) {
        // Phones: crop to fill. Wide screens (tablets, landscape): fit, with the gradient as bars.
        val crop = maxWidth / maxHeight < WIDE_RATIO
        val scale = if (crop) ContentScale.Crop else ContentScale.Fit
        Image(
            painterResource(if (reachedEnd) R.drawable.boot_video_last else R.drawable.boot_video_first),
            contentDescription = null,
            contentScale = scale,
            modifier = Modifier.fillMaxSize(),
        )
        IntroVideo(
            crop = crop,
            onFirstFrame = { firstFrameShown = true },
            onCompleted = { reachedEnd = true; end() },
            onError = end,
            visible = firstFrameShown,
            stop = ended,
        )
    }
}

@Composable
private fun IntroVideo(
    crop: Boolean,
    onFirstFrame: () -> Unit,
    onCompleted: () -> Unit,
    onError: () -> Unit,
    visible: Boolean,
    stop: Boolean,
) {
    val context = LocalContext.current
    val player = remember { MediaPlayer() }
    val audio = remember { IntroAudio(context) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.stop() }
            player.release()
            audio.release()
        }
    }
    LaunchedEffect(stop) { if (stop) runCatching { player.pause() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                alpha = 0f // revealed on the first rendered frame; the poster covers until then
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val view = this@apply
                        runCatching {
                            player.setSurface(Surface(st))
                            player.setDataSource(ctx, Uri.parse("android.resource://${ctx.packageName}/${R.raw.boot_video}"))
                            player.setAudioAttributes(
                                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
                            )
                            val withSound = audio.soundAllowed() && audio.requestFocus()
                            if (!withSound) player.setVolume(0f, 0f)
                            player.setOnVideoSizeChangedListener { _, vw, vh -> view.fitVideo(vw, vh, crop) }
                            player.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) { view.alpha = 1f; onFirstFrame() }
                                false
                            }
                            player.setOnCompletionListener { audio.release(); onCompleted() }
                            player.setOnErrorListener { _, _, _ -> audio.release(); onError(); true }
                            player.setOnPreparedListener { it.start() }
                            player.prepareAsync()
                        }.onFailure { onError() }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        if (player.videoWidth > 0) this@apply.fitVideo(player.videoWidth, player.videoHeight, crop)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        update = { it.alpha = if (visible) 1f else 0f },
    )
}

/** Centre-crop (phones) or fit (wide screens) the video inside the TextureView. */
private fun TextureView.fitVideo(videoW: Int, videoH: Int, crop: Boolean) {
    if (width == 0 || height == 0 || videoW == 0 || videoH == 0) return
    val viewRatio = width.toFloat() / height
    val videoRatio = videoW.toFloat() / videoH
    val (sx, sy) = if (crop == (videoRatio > viewRatio)) videoRatio / viewRatio to 1f else 1f to viewRatio / videoRatio
    setTransform(Matrix().apply { setScale(sx, sy, width / 2f, height / 2f) })
}

/**
 * Sound only when the phone isn't silent/vibrate and media volume is up; other audio ducks briefly
 * (transient, may-duck focus) instead of stopping, and gets focus back as soon as the intro ends.
 */
private class IntroAudio(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null

    fun soundAllowed(): Boolean =
        am.ringerMode == AudioManager.RINGER_MODE_NORMAL && am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0

    fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .build()
        request = req
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun release() {
        request?.let { am.abandonAudioFocusRequest(it) }
        request = null
    }
}

/**
 * Looney Tunes style iris-out: only the part of the intro inside a circle around the hamster stays
 * visible, so the app shows through outside it. [openness] 1 = fully open (whole screen), 0 = closed.
 * Centred on the hamster's face in the video's final frame, with a thin dark rim like the cartoon's.
 */
fun Modifier.irisOut(openness: Float): Modifier = drawWithContent {
    if (openness >= 1f) { drawContent(); return@drawWithContent }
    if (openness <= 0f) return@drawWithContent
    val center = Offset(size.width * IRIS_CENTER_X, size.height * IRIS_CENTER_Y)
    val farthest = maxOf(
        hypot(center.x, center.y), hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y),
    )
    val radius = farthest * openness
    clipPath(Path().apply { addOval(Rect(center, radius)) }) { this@drawWithContent.drawContent() }
    drawCircle(Color.Black.copy(alpha = 0.55f), radius, center, style = Stroke(width = 3.dp.toPx()))
}

/** Iris timing: close to [IRIS_HOLD] of the full radius, hold a beat on the hamster, then shut. */
const val IRIS_HOLD = 0.26f
const val IRIS_CLOSE_TO_HOLD_MS = 520
const val IRIS_HOLD_MS = 280L
const val IRIS_SHUT_MS = 300
private const val IRIS_CENTER_X = 0.5f
private const val IRIS_CENTER_Y = 0.48f

/** Whether the intro should play at all on this cold start (spec "Rules"). */
object IntroPolicy {
    private const val PREFS = "zeul_intro"
    private const val KEY_ENABLED = "startup_animation"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Cold start only; never for links/notification taps, TalkBack, or "Remove animations". */
    fun shouldPlay(context: Context, coldStart: Boolean, openedFromLinkOrPush: Boolean): Boolean {
        if (!coldStart || openedFromLinkOrPush || !isEnabled(context)) return false
        val animatorScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (animatorScale == 0f) return false
        val a11y = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return !a11y.isTouchExplorationEnabled
    }
}

private const val FIRST_FRAME_TIMEOUT_MS = 1_500L
private const val MAX_DURATION_MS = 6_000L
private const val WIDE_RATIO = 0.65f
