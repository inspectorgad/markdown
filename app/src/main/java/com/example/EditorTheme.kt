package com.example

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

data class EditorTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val bgColor: Int,
    val paperColor: Int,
    val textColor: Int,
    val headingColor: Int,
    val linkColor: Int
) {
    fun toBgColor(): Color = Color(bgColor)
    fun toPaperColor(): Color = Color(paperColor)
    fun toTextColor(): Color = Color(textColor)
    fun toHeadingColor(): Color = Color(headingColor)
    fun toLinkColor(): Color = Color(linkColor)
}

object ThemePresets {
    val Light = EditorTheme(
        id = "light",
        name = "Light Classic",
        isDark = false,
        bgColor = 0xFFF1F5F9.toInt(),
        paperColor = 0xFFFFFFFF.toInt(),
        textColor = 0xFF0F172A.toInt(),
        headingColor = 0xFF1E293B.toInt(),
        linkColor = 0xFF2563EB.toInt()
    )

    val Dark = EditorTheme(
        id = "dark",
        name = "Dark Space",
        isDark = true,
        bgColor = 0xFF0F1219.toInt(),
        paperColor = 0xFF181D26.toInt(),
        textColor = 0xFFE2E8F0.toInt(),
        headingColor = 0xFFF1F5F9.toInt(),
        linkColor = 0xFF60A5FA.toInt()
    )

    val Sepia = EditorTheme(
        id = "sepia",
        name = "Warm Sepia",
        isDark = false,
        bgColor = 0xFFEFE6D5.toInt(),
        paperColor = 0xFFFBF4E6.toInt(),
        textColor = 0xFF433422.toInt(),
        headingColor = 0xFF5C3A21.toInt(),
        linkColor = 0xFFC2410C.toInt()
    )

    val presets = listOf(Light, Dark, Sepia)

    fun getCustomTheme(sharedPrefs: SharedPreferences): EditorTheme {
        val bgColor = sharedPrefs.getInt("custom_bg_color", 0xFF1A1F2C.toInt())
        val paperColor = sharedPrefs.getInt("custom_paper_color", 0xFF232D3F.toInt())
        val textColor = sharedPrefs.getInt("custom_text_color", 0xFFE2E8F0.toInt())
        val headingColor = sharedPrefs.getInt("custom_heading_color", 0xFFFFE082.toInt())
        val linkColor = sharedPrefs.getInt("custom_link_color", 0xFF81C784.toInt())
        val isDark = sharedPrefs.getBoolean("custom_is_dark", true)

        return EditorTheme(
            id = "custom",
            name = "My Custom Design",
            isDark = isDark,
            bgColor = bgColor,
            paperColor = paperColor,
            textColor = textColor,
            headingColor = headingColor,
            linkColor = linkColor
        )
    }

    fun saveCustomTheme(sharedPrefs: SharedPreferences, theme: EditorTheme) {
        sharedPrefs.edit().apply {
            putInt("custom_bg_color", theme.bgColor)
            putInt("custom_paper_color", theme.paperColor)
            putInt("custom_text_color", theme.textColor)
            putInt("custom_heading_color", theme.headingColor)
            putInt("custom_link_color", theme.linkColor)
            putBoolean("custom_is_dark", theme.isDark)
            apply()
        }
    }
}
