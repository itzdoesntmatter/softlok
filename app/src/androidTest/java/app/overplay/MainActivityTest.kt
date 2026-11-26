// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenLaunches() {
        composeRule.onNodeWithText("Overplay").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Add app").assertIsDisplayed()
    }
}
