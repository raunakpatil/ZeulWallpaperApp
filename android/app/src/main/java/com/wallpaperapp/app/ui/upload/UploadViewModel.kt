package com.wallpaperapp.app.ui.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.R
import com.wallpaperapp.app.data.account.AuthRepository
import com.wallpaperapp.app.data.account.AuthState
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.data.upload.MyUpload
import com.wallpaperapp.app.data.upload.ProcessedUpload
import com.wallpaperapp.app.data.upload.UploadForm
import com.wallpaperapp.app.data.upload.UploadProcessor
import com.wallpaperapp.app.data.upload.UploadQuota
import com.wallpaperapp.app.data.upload.UploadRejected
import com.wallpaperapp.app.data.upload.UploadRepository
import com.wallpaperapp.app.data.upload.UploadTooSmallException
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UploadUiState(
    val photo: Uri? = null,
    val processing: Boolean = false,
    val processed: ProcessedUpload? = null,
    val title: String = "",
    val categoryId: String? = null,
    val tags: String = "",
    val rightsConfirmed: Boolean = false,
    val categories: List<Category> = emptyList(),
    val quota: UploadQuota? = null,
    val uploading: Boolean = false,
    val message: UiText? = null,
    /** Set after a successful upload: the new wallpaper's id. */
    val publishedId: String? = null,
) {
    val canPublish: Boolean
        get() = processed != null && !processing && !uploading && title.trim().length >= 2 &&
            categoryId != null && rightsConfirmed && quota?.let { it.enabled && it.remaining > 0 } != false
}

@HiltViewModel
class UploadViewModel @Inject constructor(
    auth: AuthRepository,
    private val uploads: UploadRepository,
    private val processor: UploadProcessor,
    private val wallpapers: WallpaperRepository,
    appConfig: AppConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    val signedIn: StateFlow<Boolean?> = auth.state
        .map { when (it) { is AuthState.SignedIn -> true; AuthState.Loading -> null; else -> false } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val cdnBaseUrl: StateFlow<String> = appConfig.config.map { it.cdnBaseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appConfig.config.value.cdnBaseUrl)

    private var processJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { wallpapers.categories() }.onSuccess { list -> _state.update { it.copy(categories = list) } }
        }
        // Load the quota once signed in (and again after signing in from this screen).
        viewModelScope.launch {
            signedIn.collect { if (it == true) refreshQuota() }
        }
    }

    private suspend fun refreshQuota() {
        runCatching { uploads.quota() }.onSuccess { q -> _state.update { it.copy(quota = q) } }
    }

    fun onPhotoPicked(uri: Uri?) {
        if (uri == null) return
        processJob?.cancel()
        _state.update { it.copy(photo = uri, processed = null, processing = true, message = null, publishedId = null) }
        processJob = viewModelScope.launch {
            try {
                val p = processor.process(uri)
                _state.update { it.copy(processed = p, processing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UploadTooSmallException) {
                _state.update { it.copy(photo = null, processing = false, message = UiText.Res(R.string.upload_too_small, listOf(e.width, e.height))) }
            } catch (e: Exception) {
                _state.update { it.copy(photo = null, processing = false, message = UiText.Res(R.string.upload_read_failed)) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(photo = null, processing = false, message = UiText.Res(R.string.upload_read_failed)) }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v.take(60)) }
    fun onCategory(id: String) = _state.update { it.copy(categoryId = id) }
    fun onTags(v: String) = _state.update { it.copy(tags = v.take(160)) }
    fun onRights(v: Boolean) = _state.update { it.copy(rightsConfirmed = v) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun publish() {
        val s = _state.value
        val p = s.processed ?: return
        if (!s.canPublish) return
        _state.update { it.copy(uploading = true, message = null) }
        viewModelScope.launch {
            try {
                val form = UploadForm(
                    title = s.title.trim(),
                    categoryId = s.categoryId!!,
                    tags = s.tags.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }.take(6),
                    rightsConfirmed = s.rightsConfirmed,
                )
                val result = uploads.upload(p, form)
                _state.update { UploadUiState(categories = it.categories, quota = it.quota, publishedId = result.id) }
                refreshQuota()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UploadRejected) {
                _state.update { it.copy(uploading = false, message = UiText.Raw(e.message ?: "")) }
                refreshQuota()
            } catch (e: Exception) {
                _state.update { it.copy(uploading = false, message = UiText.Res(R.string.upload_failed)) }
            }
        }
    }

    /** "Upload another" after a success. */
    fun reset() = _state.update { UploadUiState(categories = it.categories, quota = it.quota) }
}

data class MyUploadsUiState(
    val loading: Boolean = true,
    val items: List<MyUpload> = emptyList(),
    val deleting: String? = null,
    val message: UiText? = null,
)

@HiltViewModel
class MyUploadsViewModel @Inject constructor(
    private val uploads: UploadRepository,
    appConfig: AppConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MyUploadsUiState())
    val state: StateFlow<MyUploadsUiState> = _state.asStateFlow()
    val cdnBaseUrl: StateFlow<String> = appConfig.config.map { it.cdnBaseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appConfig.config.value.cdnBaseUrl)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { uploads.myUploads() }
                .onSuccess { list -> _state.update { it.copy(loading = false, items = list) } }
                .onFailure { _state.update { it.copy(loading = false, message = UiText.Res(R.string.upload_list_failed)) } }
        }
    }

    fun delete(id: String) {
        if (_state.value.deleting != null) return
        _state.update { it.copy(deleting = id) }
        viewModelScope.launch {
            runCatching { uploads.delete(id) }
                .onSuccess { _state.update { s -> s.copy(deleting = null, items = s.items.filterNot { it.id == id }) } }
                .onFailure { e -> _state.update { it.copy(deleting = null, message = (e as? UploadRejected)?.message?.let(UiText::Raw) ?: UiText.Res(R.string.upload_delete_failed)) } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
