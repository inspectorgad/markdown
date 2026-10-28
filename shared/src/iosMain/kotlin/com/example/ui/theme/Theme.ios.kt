package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS has no wallpaper palette to borrow, so the app's own scheme is used. */
@Composable
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme? = null
