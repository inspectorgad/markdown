package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.LeaderCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class LeaderCardScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun leader_card_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface {
                    LeaderCard(
                        title = "Home Runs",
                        entries = listOf(
                            "Jess Carter" to "7",
                            "Sam Ortiz" to "5",
                            "Riley Nguyen" to "4"
                        )
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/leader_card.png")
    }
}
