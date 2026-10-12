package com.wallpaperapp.app.ui.intro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Hand-off between the boot intro and Home's header. After the intro's iris-out shuts, the hamster
 * makes an encore inside Home's diamond (Premium) button: a mini iris opens on it, holds a couple of
 * seconds, then closes again on the diamond. Home reports whether its button is on screen.
 */
object IntroHandoff {
    /** Centre of Home's diamond button in window pixels; null when Home isn't on screen. */
    var gemCenter by mutableStateOf<Offset?>(null)

    /** Set when the intro ends; Home plays the encore, then clears it. */
    var hamsterInGem by mutableStateOf(false)
}
