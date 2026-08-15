package com.examsystem.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RedPrimary = Color(0xFFB71C1C)
val RedDark = Color(0xFF210002)
val RedAccent = Color(0xFFE91E63)
val White = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF5F5F5)

private val DarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    secondary = RedAccent,
    tertiary = Color(0xFF880E4F),
    background = RedDark,
    surface = Color(0xFF1A1A1A),
    onPrimary = White,
    onSecondary = White,
    onBackground = White,
    onSurface = White,
)

private val LightColorScheme = lightColorScheme(
    primary = RedPrimary,
    secondary = RedAccent,
    tertiary = Color(0xFF880E4F),
    background = OffWhite,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun ExamSystemTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
