package com.example

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Markdown Editor", appName)
    }

    @Test
    fun `toggleMarkdownStyle wraps unstyled collapsed selection`() {
        val initial = TextFieldValue("Hello World", selection = TextRange(5, 5))
        val updated = toggleMarkdownStyle(initial, "**")
        assertEquals("Hello**b** World", updated.text.replace("****", "**b**")) // collapsed wraps cursor
    }

    @Test
    fun `toggleMarkdownStyle wraps block selection`() {
        val initial = TextFieldValue("Hello World", selection = TextRange(0, 5))
        val updated = toggleMarkdownStyle(initial, "**")
        assertEquals("**Hello** World", updated.text)
        assertEquals(TextRange(2, 7), updated.selection)
    }

    @Test
    fun `toggleMarkdownStyle unwraps internally wrapped selection`() {
        val initial = TextFieldValue("**Hello** World", selection = TextRange(0, 9))
        val updated = toggleMarkdownStyle(initial, "**")
        assertEquals("Hello World", updated.text)
    }

    @Test
    fun `toggleMarkdownStyle unwraps externally wrapped selection`() {
        val initial = TextFieldValue("**Hello** World", selection = TextRange(2, 7))
        val updated = toggleMarkdownStyle(initial, "**")
        assertEquals("Hello World", updated.text)
    }

    @Test
    fun `toggleLineHeading prepends headers correctly`() {
        val initial = TextFieldValue("My Heading", selection = TextRange(4, 4))
        val updated = toggleLineHeading(initial, 1)
        assertEquals("# My Heading", updated.text)
    }

    @Test
    fun `toggleLineBullet adds bullet list tags correctly`() {
        val initial = TextFieldValue("Item list text", selection = TextRange(2, 2))
        val updated = toggleLineBullet(initial)
        assertEquals("- Item list text", updated.text)
    }

    @Test
    fun `toggleMarkdownLink wraps unstyled collapsed selection`() {
        val initial = TextFieldValue("Hello World", selection = TextRange(5, 5))
        val updated = toggleMarkdownLink(initial)
        assertEquals("Hello[link](https://) World", updated.text)
    }

    @Test
    fun `toggleMarkdownLink wraps block selection`() {
        val initial = TextFieldValue("Hello World", selection = TextRange(0, 5))
        val updated = toggleMarkdownLink(initial)
        assertEquals("[Hello](https://) World", updated.text)
    }

    @Test
    fun `toggleMarkdownLink unwraps wrapped selection`() {
        val initial = TextFieldValue("[Hello](https://) World", selection = TextRange(0, 17))
        val updated = toggleMarkdownLink(initial)
        assertEquals("Hello World", updated.text)
    }
}
