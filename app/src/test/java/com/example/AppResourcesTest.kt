package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppResourcesTest {

    @Test
    fun `app name is KC Diamonds Stats`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("KC Diamonds Stats", context.getString(R.string.app_name))
    }

    // The launcher icon is assembled from generated bitmaps rather than the
    // template vector, so make sure every layer actually resolves.
    @Test
    fun `launcher icon layers all resolve`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_foreground))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_monochrome))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_background))
    }
}
