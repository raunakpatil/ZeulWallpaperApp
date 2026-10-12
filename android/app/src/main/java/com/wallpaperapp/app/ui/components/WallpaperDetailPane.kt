package com.wallpaperapp.app.ui.components

import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.foundation.Image
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.imageUrl

/** Right-hand pane of the large-screen list-detail layout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperDetailPane(wallpaper: Wallpaper?, cdnBaseUrl: String, onOpen: (Wallpaper) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        if (wallpaper == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.pane_pick),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Surface
        }
        val blur = remember(wallpaper.blurhash) { wallpaper.blurhash?.let { BlurHash.decodeImageBitmap(it) } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageMaxHeight = maxHeight * 0.6f // keep the title and "Open" in view
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .heightIn(max = imageMaxHeight)
                    .clip(RoundedCornerShape(ZeulRadius.Card))
                    .background(parseHexColor(wallpaper.dominantColor) ?: MaterialTheme.colorScheme.surfaceVariant),
            ) {
                // The wallpaper's own shape, as wide as fits under the height cap.
                Box(Modifier.aspectRatio(tileAspectRatio(wallpaper), matchHeightConstraintsFirst = true)) {
                    if (blur != null) Image(blur, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    AsyncImage(
                        model = wallpaper.imageUrl(cdnBaseUrl, ImageVariant.PREVIEW),
                        contentDescription = wallpaper.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(wallpaper.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val details = listOfNotNull(
                wallpaper.authorName?.let { stringResource(R.string.by_author, it) },
                if (wallpaper.width != null && wallpaper.height != null) "${wallpaper.width} × ${wallpaper.height}" else null,
                wallpaper.categoryName,
                if (wallpaper.isPremium) stringResource(R.string.premium) else null,
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (wallpaper.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    wallpaper.tags.forEach { tag ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("#$tag", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            AccentButton(stringResource(R.string.pane_open), { onOpen(wallpaper) }, Modifier.fillMaxWidth())
        }
        }
    }
}
