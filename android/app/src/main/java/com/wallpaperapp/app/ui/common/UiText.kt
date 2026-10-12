package com.wallpaperapp.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text produced outside the UI (e.g. a ViewModel message), resolved to the user's language only
 * when shown. [Raw] is for text that is already final, such as a server error message.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Res -> stringResource(id, *args.toTypedArray())
        is Raw -> text
        is Plural -> pluralStringResource(id, count, *args.toTypedArray())
    }

    fun resolve(context: android.content.Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
        is Raw -> text
        is Plural -> context.resources.getQuantityString(id, count, *args.toTypedArray())
    }
}
