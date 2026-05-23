package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

class MarkdownVisualTransformation(
    private val isDark: Boolean,
    private val baseFontSize: Float,
    private val textColor: Color,
    private val headingColor: Color,
    private val linkColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedBuilder = AnnotatedString.Builder(text.text)
        val plainText = text.text
        
        // Muted color marker style for syntax characters like #, **, *, etc.
        val markerColor = if (isDark) Color(0x3BFFFFFF) else Color(0x40000000)
        val markerStyle = SpanStyle(
            color = markerColor,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace
        )
        
        val lines = plainText.split("\n")
        var currentPosition = 0
        
        for (line in lines) {
            val lineLength = line.length
            
            if (line.startsWith("# ")) {
                // H1 - Title Heading
                annotatedBuilder.addStyle(markerStyle, currentPosition, currentPosition + 2)
                annotatedBuilder.addStyle(
                    SpanStyle(
                        fontSize = (baseFontSize * 1.55f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = headingColor
                    ),
                    currentPosition + 2,
                    currentPosition + lineLength
                )
            } else if (line.startsWith("## ")) {
                // H2 - Secondary Heading
                annotatedBuilder.addStyle(markerStyle, currentPosition, currentPosition + 3)
                annotatedBuilder.addStyle(
                    SpanStyle(
                        fontSize = (baseFontSize * 1.35f).sp,
                        fontWeight = FontWeight.Bold,
                        color = headingColor.copy(alpha = 0.9f)
                    ),
                    currentPosition + 3,
                    currentPosition + lineLength
                )
            } else if (line.startsWith("### ")) {
                // H3 - Tertiary Heading
                annotatedBuilder.addStyle(markerStyle, currentPosition, currentPosition + 4)
                annotatedBuilder.addStyle(
                    SpanStyle(
                        fontSize = (baseFontSize * 1.20f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = headingColor.copy(alpha = 0.82f)
                    ),
                    currentPosition + 4,
                    currentPosition + lineLength
                )
            } else if (line.startsWith("> ")) {
                // Blockquote
                annotatedBuilder.addStyle(markerStyle, currentPosition, currentPosition + 2)
                annotatedBuilder.addStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.7f)
                    ),
                    currentPosition + 2,
                    currentPosition + lineLength
                )
            } else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                // Bulleted list bullet coloring
                val markerIdx = line.indexOfFirst { it == '-' || it == '*' }
                if (markerIdx >= 0) {
                    annotatedBuilder.addStyle(
                        SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.Bold
                        ),
                        currentPosition + markerIdx,
                        currentPosition + markerIdx + 2
                    )
                }
            } else if (line.matches(NUMBER_LIST_REGEX)) {
                // Numbered list styling
                val spaceIndex = line.indexOf(' ')
                if (spaceIndex >= 0) {
                    annotatedBuilder.addStyle(
                        SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.Bold
                        ),
                        currentPosition,
                        currentPosition + spaceIndex + 1
                    )
                }
            } else if (line.startsWith("---") || line.startsWith("***")) {
                // Horizontal divider lines
                annotatedBuilder.addStyle(
                    SpanStyle(
                        color = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    currentPosition,
                    currentPosition + lineLength
                )
            }
            
            currentPosition += lineLength + 1
        }
        
        // Style multi-line code block: ```code```
        MULTI_LINE_CODE_REGEX.findAll(plainText).forEach { matchResult ->
            val range = matchResult.range
            annotatedBuilder.addStyle(markerStyle, range.start, range.start + 3)
            annotatedBuilder.addStyle(markerStyle, range.endInclusive - 2, range.endInclusive + 1)
            annotatedBuilder.addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = (baseFontSize * 0.9f).sp,
                    color = textColor,
                    background = if (isDark) Color(0x33334155) else Color(0x0C475569)
                ),
                range.start + 3,
                range.endInclusive - 2
            )
        }
        
        // Bold formatting: **text**
        BOLD_REGEX.findAll(plainText).forEach { matchResult ->
            val range = matchResult.range
            annotatedBuilder.addStyle(markerStyle, range.start, range.start + 2)
            annotatedBuilder.addStyle(markerStyle, range.endInclusive - 1, range.endInclusive + 1)
            annotatedBuilder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                range.start + 2,
                range.endInclusive - 1
            )
        }
        
        // Italic formatting: *text* (avoiding double asterisks)
        ITALIC_REGEX.findAll(plainText).forEach { matchResult ->
            val range = matchResult.range
            annotatedBuilder.addStyle(markerStyle, range.start, range.start + 1)
            annotatedBuilder.addStyle(markerStyle, range.endInclusive, range.endInclusive + 1)
            annotatedBuilder.addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                range.start + 1,
                range.endInclusive
            )
        }
        
        // Inline code formatting: `code`
        CODE_REGEX.findAll(plainText).forEach { matchResult ->
            val range = matchResult.range
            annotatedBuilder.addStyle(markerStyle, range.start, range.start + 1)
            annotatedBuilder.addStyle(markerStyle, range.endInclusive, range.endInclusive + 1)
            annotatedBuilder.addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = (baseFontSize * 0.95f).sp,
                    color = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                    background = if (isDark) Color(0x33475569) else Color(0x1F64748B)
                ),
                range.start + 1,
                range.endInclusive
            )
        }

        // Hyperlinks formatting: [text](url)
        LINK_REGEX.findAll(plainText).forEach { matchResult ->
            val range = matchResult.range
            val groups = matchResult.groups
            val textGroup = groups[1]
            val urlGroup = groups[2]
            
            if (textGroup != null && urlGroup != null) {
                // Dim brackets: '[' and '](...)'
                val startBracketStart = range.start
                val startBracketEnd = textGroup.range.first
                val middleBracketStart = textGroup.range.last + 1
                val middleBracketEnd = urlGroup.range.first
                val endBracketStart = urlGroup.range.last + 1
                val endBracketEnd = range.endInclusive + 1
                
                annotatedBuilder.addStyle(markerStyle, startBracketStart, startBracketEnd) // [
                annotatedBuilder.addStyle(markerStyle, middleBracketStart, middleBracketEnd) // ](
                annotatedBuilder.addStyle(markerStyle, endBracketStart, endBracketEnd) // )

                // Underline and highlight the link text as a polished link color
                annotatedBuilder.addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    ),
                    textGroup.range.first,
                    textGroup.range.last + 1
                )
                
                // Mute and italicize the URL coordinate
                annotatedBuilder.addStyle(
                    SpanStyle(
                        fontSize = (baseFontSize * 0.85f).sp,
                        color = textColor.copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic
                    ),
                    urlGroup.range.first,
                    urlGroup.range.last + 1
                )
            }
        }
        
        return TransformedText(
            annotatedBuilder.toAnnotatedString(),
            OffsetMapping.Identity
        )
    }

    companion object {
        private val MULTI_LINE_CODE_REGEX = Regex("(?s)```(.*?)```")
        private val BOLD_REGEX = Regex("\\*\\*([^*]+)\\*\\*")
        private val ITALIC_REGEX = Regex("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)")
        private val CODE_REGEX = Regex("`([^`]+)`")
        private val LINK_REGEX = Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
        private val NUMBER_LIST_REGEX = Regex("^\\s*\\d+\\.\\s+.*")
    }
}
