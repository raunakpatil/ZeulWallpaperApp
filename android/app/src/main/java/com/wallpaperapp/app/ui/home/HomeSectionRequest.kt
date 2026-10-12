package com.wallpaperapp.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A `zeul://section/{name}` link asking Home to open one of its chips. Home takes it once. */
@Singleton
class HomeSectionRequest @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun open(sectionKey: String) {
        _pending.value = sectionKey
    }

    fun consumed() {
        _pending.value = null
    }
}
