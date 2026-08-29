package com.pixelpy.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newProjectShowsInitialNotebookInsideSelector() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val project = File(context.cacheDir, "notebook-ui-${System.nanoTime()}").apply { mkdirs() }

        composeRule.setContent {
            MaterialTheme {
                NotebookScreen(project = project, onBack = {}, onArtifactsChanged = {})
            }
        }

        composeRule.onNodeWithText("ANÁLISIS INICIAL").performClick()
        composeRule.onNodeWithText("MIS NOTEBOOKS").assertIsDisplayed()
        composeRule.onNodeWithText("Análisis inicial").assertIsDisplayed()
        composeRule.onNodeWithText("ACTIVO").assertIsDisplayed()
    }
}
