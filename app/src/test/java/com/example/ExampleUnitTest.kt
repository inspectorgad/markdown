package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testMarkdownVisualTransformationWithEdgeCases() {
        val transformer = MarkdownVisualTransformation(
            isDark = true,
            baseFontSize = 18f,
            textColor = Color.White,
            headingColor = Color.Blue,
            linkColor = Color.Green
        )

        val edgeCaseTexts = listOf(
            "",
            "\n",
            "\n\n",
            "#",
            "# ",
            "##",
            "## ",
            "###",
            "### ",
            "-",
            "- ",
            "*",
            "* ",
            ">",
            "> ",
            "---",
            "***",
            "1. text",
            "  1. text",
            "  - bullet",
            "````",
            "```\n```",
            "```text\n```",
            "**bold**",
            "**",
            "***",
            "****",
            "*italic*",
            "*",
            "[link](url)",
            "[link]()",
            "[](url)",
            "[]()",
            "[-]",
            "[-]()",
            "[text](",
            "[text])",
            "# Heading 1\n## Heading 2\n\n- Bullet\n  - Nested\n\n```\ncode block\n```\n\n**bold** and *italic* and [link](google) and `code` test."
        )

        for (text in edgeCaseTexts) {
            try {
                val result = transformer.filter(AnnotatedString(text))
                assertNotNull(result)
                assertNotNull(result.text)
                // Ensure OffsetMapping is correct too
                val mapping = result.offsetMapping
                if (text.isNotEmpty()) {
                    val transformedLen = result.text.length
                    // Test all bounds for offset mapping
                    for (i in 0..text.length) {
                        val mapped = mapping.originalToTransformed(i)
                        assertTrue("Mapped offset $mapped out of bounds for transformed length $transformedLen", mapped in 0..transformedLen)
                    }
                    for (i in 0..transformedLen) {
                        val mapped = mapping.transformedToOriginal(i)
                        assertTrue("Reverse mapped offset $mapped out of bounds for original length ${text.length}", mapped in 0..text.length)
                    }
                }
            } catch (e: Exception) {
                fail("Failed on text: \"$text\" with error: ${e.message}\n${e.stackTraceToString()}")
            }
        }
    }
}

