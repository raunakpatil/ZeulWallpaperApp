package com.wallpaperapp.app.ui.preview

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Rough stand-ins for Android's lock and home screens, so users can judge a wallpaper in place. */
@Composable
fun DeviceOverlay(mode: OverlayMode, modifier: Modifier = Modifier) {
    when (mode) {
        OverlayMode.NONE -> Unit
        OverlayMode.LOCK -> LockScreenOverlay(modifier)
        OverlayMode.HOME -> HomeScreenOverlay(modifier)
    }
}

@Composable
private fun LockScreenOverlay(modifier: Modifier) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000L * (60 - value.second)) // tick on the minute
        }
    }
    val hourPattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH" else "hh"
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(top = 72.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("$hourPattern\nmm")),
            color = Color.White,
            fontSize = 96.sp,
            lineHeight = 92.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HomeScreenOverlay(modifier: Modifier) {
    val icon = Color.White.copy(alpha = 0.85f)
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        // At-a-glance widget
        Box(Modifier.padding(top = 64.dp).size(width = 140.dp, height = 18.dp).background(icon, RoundedCornerShape(9.dp)))
        Spacer(Modifier.weight(1f))
        repeat(2) {
            AppIconRow(icon)
            Spacer(Modifier.height(20.dp))
        }
        AppIconRow(icon) // dock
        Spacer(Modifier.height(16.dp))
        // Search bar
        Box(Modifier.fillMaxWidth().height(48.dp).background(icon, RoundedCornerShape(24.dp)))
    }
}

@Composable
private fun AppIconRow(color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(4) {
            Box(Modifier.size(56.dp).background(color, CircleShape))
        }
    }
}
