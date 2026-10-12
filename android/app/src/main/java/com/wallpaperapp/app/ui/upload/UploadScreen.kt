package com.wallpaperapp.app.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wallpaperapp.app.R
import com.wallpaperapp.app.data.upload.UploadProcessor
import com.wallpaperapp.app.ui.theme.AccentButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.SoftButton
import com.wallpaperapp.app.ui.theme.SoftChip
import com.wallpaperapp.app.ui.theme.SoftSectionLabel
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.SoftTextField
import com.wallpaperapp.app.ui.theme.SoftTopBar
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.theme.softInset
import com.wallpaperapp.app.ui.theme.softRaised

/** Content rules the uploader agrees to (terms page, "User uploads" section). */
private const val TERMS_URL = "https://raunakpatil.github.io/wallpaper-app-site/terms.html#uploads"

/** Upload a wallpaper (Raunak, 2026-10-11): pick a photo, name it, pick a category, publish. Signed-in users only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onMyUploads: () -> Unit,
    onOpenWallpaper: (String) -> Unit,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val soft = LocalSoft.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onPhotoPicked(it) }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(Modifier.fillMaxSize().background(soft.base).statusBarsPadding().navigationBarsPadding()) {
        SoftTopBar(onBack, stringResource(R.string.back), title = stringResource(R.string.upload_title)) {
            if (signedIn == true) SoftTextButton(stringResource(R.string.my_uploads), onMyUploads)
        }
        when {
            signedIn == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = soft.accent) }
            signedIn == false -> SignInPrompt(onSignIn)
            state.publishedId != null -> Published(
                onOpen = { onOpenWallpaper(state.publishedId!!) },
                onAnother = viewModel::reset,
                onMyUploads = onMyUploads,
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.quota?.let { q ->
                    Text(
                        when {
                            !q.enabled -> stringResource(R.string.upload_paused)
                            q.remaining == 0 -> stringResource(R.string.upload_limit_reached, q.dailyLimit)
                            else -> stringResource(R.string.upload_remaining, q.remaining, q.dailyLimit)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (q.enabled && q.remaining > 0) soft.textMuted else soft.accent,
                    )
                }
                PhotoBox(state, pickPhoto)

                SoftSectionLabel(stringResource(R.string.upload_name_label))
                SoftTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitle,
                    placeholder = stringResource(R.string.upload_name_hint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )

                SoftSectionLabel(stringResource(R.string.upload_category_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.categories.forEach { c ->
                        SoftChip(c.name, selected = state.categoryId == c.id, onClick = { viewModel.onCategory(c.id) })
                    }
                }

                SoftSectionLabel(stringResource(R.string.upload_tags_label))
                SoftTextField(
                    value = state.tags,
                    onValueChange = viewModel::onTags,
                    placeholder = stringResource(R.string.upload_tags_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )

                RightsRow(state.rightsConfirmed, viewModel::onRights)

                state.message?.let { msg ->
                    Text(msg.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                AccentButton(
                    stringResource(R.string.upload_publish),
                    viewModel::publish,
                    Modifier.fillMaxWidth(),
                    enabled = state.canPublish,
                    loading = state.uploading,
                    leading = ZeulIcons.Upload,
                )
                Text(
                    stringResource(R.string.upload_public_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = soft.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoBox(state: UploadUiState, onPick: () -> Unit) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    val shape = RoundedCornerShape(ZeulRadius.Card)
    val label = stringResource(if (state.photo == null) R.string.upload_choose_photo else R.string.upload_change_photo)
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (state.photo == null) Modifier.height(220.dp) else Modifier.aspectRatio(state.processed?.let { it.width.toFloat() / it.height }?.coerceIn(0.4f, 1.8f) ?: 0.75f))
            .pressScale(press, 0.02f)
            .then(if (state.photo == null) Modifier.softInset(shape, depth = 4.dp) else Modifier.softRaised(shape, depth = 6.dp))
            .softClickable(press, label, haptic = HapticFeedbackType.ContextClick, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (state.photo == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
                Box(Modifier.size(56.dp).softRaised(CircleShape, depth = 4.dp, fill = soft.accent), contentAlignment = Alignment.Center) {
                    Icon(ZeulIcons.Upload, null, tint = soft.onAccent, modifier = Modifier.size(26.dp))
                }
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = soft.text)
                Text(
                    stringResource(R.string.upload_photo_rules, UploadProcessor.MIN_SHORT_EDGE, UploadProcessor.MIN_LONG_EDGE),
                    style = MaterialTheme.typography.bodySmall,
                    color = soft.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            AsyncImage(model = state.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(shape))
            if (state.processing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        Text(stringResource(R.string.upload_preparing), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(ZeulRadius.Chip))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RightsRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val soft = LocalSoft.current
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = soft.accent, checkmarkColor = soft.onAccent, uncheckedColor = soft.textMuted),
        )
        Column(Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.upload_rights), style = MaterialTheme.typography.bodyMedium, color = soft.text)
            Text(
                stringResource(R.string.upload_rules_link),
                style = MaterialTheme.typography.bodyMedium,
                color = soft.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp).softClickable(rememberSoftPress(), null) { uriHandler.openUri(TERMS_URL) },
            )
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    val soft = LocalSoft.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Hamster()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.upload_sign_in_title), fontFamily = ZeulDisplay, fontSize = 26.sp, color = soft.wordmark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.upload_sign_in_body), style = MaterialTheme.typography.bodyMedium, color = soft.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        AccentButton(stringResource(R.string.sign_in), onSignIn, Modifier.widthIn(min = 220.dp))
    }
}

@Composable
private fun Published(onOpen: () -> Unit, onAnother: () -> Unit, onMyUploads: () -> Unit) {
    val soft = LocalSoft.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(150.dp)) {
            Box(Modifier.align(Alignment.Center)) { Hamster() }
            Box(Modifier.size(46.dp).align(Alignment.BottomEnd).softRaised(CircleShape, depth = 4.dp, fill = soft.accent), contentAlignment = Alignment.Center) {
                Icon(ZeulIcons.Check, null, tint = soft.onAccent, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.upload_done_title), fontFamily = ZeulDisplay, fontSize = 28.sp, color = soft.wordmark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.upload_done_body), style = MaterialTheme.typography.bodyMedium, color = soft.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        AccentButton(stringResource(R.string.upload_another), onAnother, Modifier.fillMaxWidth().widthIn(max = 360.dp), leading = ZeulIcons.Upload)
        Spacer(Modifier.height(12.dp))
        SoftButton(stringResource(R.string.my_uploads), onMyUploads, Modifier.fillMaxWidth().widthIn(max = 360.dp))
    }
}

@Composable
private fun Hamster() {
    Box(Modifier.size(128.dp).softRaised(CircleShape, depth = 8.dp), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.boot_splash_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(120.dp).clip(CircleShape).graphicsLayer { scaleX = 1.35f; scaleY = 1.35f },
        )
    }
}

/** The signed-in user's uploads: live or hidden (after reports), with delete. */
@Composable
fun MyUploadsScreen(
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onOpenWallpaper: (String) -> Unit,
    viewModel: MyUploadsViewModel = hiltViewModel(),
) {
    val soft = LocalSoft.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cdn by viewModel.cdnBaseUrl.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(soft.base).statusBarsPadding().navigationBarsPadding()) {
        SoftTopBar(onBack, stringResource(R.string.back), title = stringResource(R.string.my_uploads))
        state.message?.let { msg ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                SoftTextButton(stringResource(R.string.ok), viewModel::dismissMessage)
            }
        }
        when {
            state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = soft.accent) }
            state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Hamster()
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.my_uploads_empty), style = MaterialTheme.typography.titleMedium, color = soft.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                AccentButton(stringResource(R.string.upload_title), onUpload, leading = ZeulIcons.Upload)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val shape = RoundedCornerShape(ZeulRadius.Card)
                        val press = rememberSoftPress()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.62f)
                                .softRaised(shape, depth = 4.dp)
                                .then(if (item.isLive) Modifier.softClickable(press, item.title) { onOpenWallpaper(item.id) } else Modifier),
                        ) {
                            AsyncImage(
                                model = if (cdn.isBlank()) null else "${cdn.trimEnd('/')}/${item.storageKey}/thumb.webp",
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(shape),
                            )
                            if (!item.isLive) {
                                Text(
                                    stringResource(R.string.my_uploads_hidden),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(ZeulRadius.Small))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            IconButton(
                                onClick = { confirm = item.id },
                                enabled = state.deleting == null,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp),
                            ) {
                                if (state.deleting == item.id) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Box(Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                    Icon(ZeulIcons.Close, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Text(item.title, style = MaterialTheme.typography.labelLarge, color = soft.text, maxLines = 1)
                        Text(
                            stringResource(R.string.my_uploads_stats, item.favCount, item.downloadCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = soft.textMuted,
                        )
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        }
    }

    confirm?.let { id ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.my_uploads_delete_title)) },
            text = { Text(stringResource(R.string.my_uploads_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = null; viewModel.delete(id) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
