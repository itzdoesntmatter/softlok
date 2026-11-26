// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F3D3E),
    onPrimary = Color(0xFFF6E7C1),
    primaryContainer = Color(0xFFC8E9E3),
    onPrimaryContainer = Color(0xFF05201F),
    secondary = Color(0xFF50625E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E6E2),
    onSecondaryContainer = Color(0xFF0D1F1C),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF13201E),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF13201E),
    surfaceVariant = Color(0xFFE4ECE9),
    onSurfaceVariant = Color(0xFF44534F),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD8CE),
    onPrimary = Color(0xFF06201F),
    primaryContainer = Color(0xFF2B5553),
    onPrimaryContainer = Color(0xFFD3F2EC),
    secondary = Color(0xFFB7CCC7),
    onSecondary = Color(0xFF223431),
    secondaryContainer = Color(0xFF394B47),
    onSecondaryContainer = Color(0xFFD7E6E2),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE7EFEC),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE7EFEC),
    surfaceVariant = Color(0xFF25322F),
    onSurfaceVariant = Color(0xFFB5C5C1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun SoftlokTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
