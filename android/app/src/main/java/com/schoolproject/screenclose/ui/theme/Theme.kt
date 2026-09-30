package com.schoolproject.screenclose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF1F3A5F), secondary = Color(0xFF2E7D5B))
private val Dark = darkColorScheme(primary = Color(0xFF9CC3F5), secondary = Color(0xFF7FD6AE))

@Composable
fun ScreenCloseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
