package com.wangzan101.mika.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalAttachmentsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun personalComposerOpensAndClosesTheSharedAttachmentPanel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MikaApp
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${app.packageName} $permission").use {
            java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
        }
        compose.setContent {
            MikaTheme {
                PersonalAgentScreen(app.personal, app.voice, {}, {}, app.memory, {}, {})
            }
        }
        compose.onNodeWithContentDescription(AttachCopy.ADD).assertIsDisplayed().performClick()
        compose.onNodeWithTag("attach-panel").assertIsDisplayed()
        compose.onNodeWithText(AttachCopy.CAMERA).assertIsDisplayed()
        compose.onNodeWithText(AttachCopy.ALBUM).assertIsDisplayed()
        compose.onNodeWithText(AttachCopy.FILES).assertIsDisplayed()
        compose.onNodeWithContentDescription(AttachCopy.CLOSE).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("attach-panel").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("attach-panel").assertDoesNotExist()
    }
}
