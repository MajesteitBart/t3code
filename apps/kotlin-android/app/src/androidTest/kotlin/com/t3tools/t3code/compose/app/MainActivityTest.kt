package com.t3tools.t3code.compose.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun foundationRootRenders() {
    composeRule.onNodeWithTag(FoundationSemantics.Root).assertIsDisplayed()
    composeRule.onNodeWithText("Native Android foundation").assertIsDisplayed()
  }
}
