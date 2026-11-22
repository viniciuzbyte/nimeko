package com.nimeko.stream

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF0A84FF),
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93),
    outlineVariant = Color(0x2E3C3C43)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF0A84FF),
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    outlineVariant = Color(0x99545458)
)

@Composable
fun NimekoTheme(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)),
        content = content
    )
}
