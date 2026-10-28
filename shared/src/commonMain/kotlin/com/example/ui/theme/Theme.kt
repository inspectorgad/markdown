package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

/**
 * The colours the phone itself offers, or null when it offers none.
 *
 * On Android 12 and newer this is the wallpaper palette (Material You), which
 * is what the app has always shown there, so an Android phone's colours do not
 * change at all. The iPhone has no equivalent, so it falls back to the fixed
 * scheme above - the same one an Android phone below 12 has always used.
 */
@Composable
expect fun platformColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic colour is available on Android 12+; false forces the fixed scheme.
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val dynamic = if (dynamicColor) platformColorScheme(darkTheme) else null
  val colorScheme = dynamic ?: if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
