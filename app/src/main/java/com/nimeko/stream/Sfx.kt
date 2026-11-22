package com.nimeko.stream

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/**
 * Lightweight, asset-free UI feedback: plays the system's built-in click tone
 * (respects the user's Settings > Sound > "Touch sounds" toggle, so it never
 * surprises anyone who has turned it off) and fires a short haptic tick.
 * No audio files needed — everything is driven by the platform.
 */
@Composable
private fun rememberFeedback(): () -> Unit {
    val view = LocalView.current
    return {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** Drop-in replacement for `Modifier.clickable { }` that adds click SFX + haptics. */
@Composable
fun Modifier.sfxClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    val feedback = rememberFeedback()
    return this.clickable(enabled = enabled, role = role) {
        feedback()
        onClick()
    }
}

/** Same idea, for composables (Poster, Banner) that build their own interaction source / ripple. */
@Composable
fun rememberSfxOnClick(onClick: () -> Unit): () -> Unit {
    val feedback = rememberFeedback()
    return { feedback(); onClick() }
}
