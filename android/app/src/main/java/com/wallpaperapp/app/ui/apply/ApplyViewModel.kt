package com.wallpaperapp.app.ui.apply

import android.app.Activity
import com.wallpaperapp.app.R
import com.wallpaperapp.app.ui.common.UiText
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wallpaperapp.app.data.image.BitmapEdits
import com.wallpaperapp.app.data.repository.WallpaperActions
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.EditParams
import com.wallpaperapp.app.domain.model.NormalizedCrop
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.common.ReviewPrompter
import com.wallpaperapp.app.ui.navigation.ApplyRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ApplyUiState(
    val wallpaper: Wallpaper? = null,
    val source: Bitmap? = null,
    /** [source] with the current blur, shown by the editor (null = no blur). */
    val blurred: Bitmap? = null,
    val edits: EditParams = EditParams(),
    val loadError: Boolean = false,
    val applying: ApplyTarget? = null,
    val message: UiText? = null,
    /** One-shot after a successful apply: a good moment to ask for a Play review. */
    val askReview: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ApplyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WallpaperRepository,
    private val actions: WallpaperActions,
    private val reviewPrompter: ReviewPrompter,
) : ViewModel() {

    private val wallpaperId = savedStateHandle.toRoute<ApplyRoute>().wallpaperId
    private val _state = MutableStateFlow(ApplyUiState())
    val state: StateFlow<ApplyUiState> = _state.asStateFlow()

    /** Called once the editor knows the screen size, so the image is decoded at a useful size. */
    fun load(maxLongEdge: Int) {
        if (_state.value.source != null) return
        viewModelScope.launch {
            try {
                val wallpaper = repository.getWallpaper(wallpaperId) ?: error("Unknown wallpaper $wallpaperId")
                val bitmap = actions.loadForEditing(wallpaper, maxLongEdge)
                _state.update { it.copy(wallpaper = wallpaper, source = bitmap, loadError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "load failed", e)
                _state.update { it.copy(loadError = true) }
            }
        }
    }

    init {
        // Re-blur the preview only after the slider settles.
        viewModelScope.launch {
            _state.map { it.source to it.edits.blur }
                .distinctUntilChanged()
                .debounce(120)
                .collect { (source, blur) ->
                    if (source == null) return@collect
                    val blurred = if (blur > 0f) withContext(Dispatchers.Default) { BitmapEdits.blur(source, blur) } else null
                    _state.update { it.copy(blurred = blurred) }
                }
        }
    }

    fun setBlur(value: Float) = _state.update { it.copy(edits = it.edits.copy(blur = value)) }
    fun setDim(value: Float) = _state.update { it.copy(edits = it.edits.copy(dim = value)) }
    fun reset() = _state.update { it.copy(edits = EditParams()) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun askForReview(activity: Activity) {
        _state.update { it.copy(askReview = false) }
        viewModelScope.launch { reviewPrompter.onSuccess(activity) }
    }

    fun apply(target: ApplyTarget, crop: NormalizedCrop, outWidth: Int, outHeight: Int) {
        val current = _state.value
        val wallpaper = current.wallpaper ?: return
        val source = current.source ?: return
        if (current.applying != null) return
        _state.update { it.copy(applying = target) }
        viewModelScope.launch {
            var applied = false
            val message = try {
                actions.apply(wallpaper, source, current.edits, crop, outWidth, outHeight, target)
                applied = true
                when (target) {
                    ApplyTarget.HOME -> UiText.Res(R.string.set_home)
                    ApplyTarget.LOCK -> UiText.Res(R.string.set_lock)
                    ApplyTarget.BOTH -> UiText.Res(R.string.set_both)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "apply failed", e)
                UiText.Res(R.string.set_failed)
            }
            _state.update { it.copy(applying = null, message = message, askReview = applied) }
        }
    }

    private companion object {
        const val TAG = "ApplyViewModel"
    }
}
