package com.nimeko.stream

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DDFF),
    onPrimaryContainer = Color(0xFF2A0A80),
    secondary = Color(0xFFFF4D8D),
    tertiary = Color(0xFF00B8D4),
    background = Color(0xFFF6F2FF),
    surface = Color.White,
    onBackground = Color(0xFF16112B),
    onSurface = Color(0xFF16112B),
    surfaceVariant = Color(0xFFE9E2FA),
    onSurfaceVariant = Color(0xFF6B6485),
    outlineVariant = Color(0x336B6485)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9D7CFF),
    onPrimary = Color(0xFF1A0A4D),
    primaryContainer = Color(0xFF3A2A7A),
    onPrimaryContainer = Color(0xFFE6DDFF),
    secondary = Color(0xFFFF6FA5),
    tertiary = Color(0xFF3DDCF5),
    background = Color(0xFF0D0A1F),
    surface = Color(0xFF1A1533),
    onBackground = Color(0xFFF2EEFF),
    onSurface = Color(0xFFF2EEFF),
    surfaceVariant = Color(0xFF2A2347),
    onSurfaceVariant = Color(0xFFA9A2C7),
    outlineVariant = Color(0x66A9A2C7)
)

@Composable
fun NimekoTheme(dark: Boolean, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)),
        content = content
    )
}
