package com.pixelpy.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextActionsTest {
    @Test fun selectionEndingAtNextLineDoesNotIndentThatLine() {
        val result = editSelectedLines(TextFieldValue("a\nb\nc", TextRange(0, 4)), "indent")
        assertEquals("    a\n    b\nc", result.text)
        assertEquals(TextRange(0, 11), result.selection)
    }
    @Test fun commentRoundTripPreservesIndentationAndBlankLines() {
        val value = TextFieldValue("    print(1)\n\n    print(2)", TextRange(0, 25))
        val commented = editSelectedLines(value, "comment")
        assertEquals("    # print(1)\n\n    # print(2)", commented.text)
        assertEquals(value.text, editSelectedLines(commented, "comment").text)
    }
    @Test fun outdentHandlesTabsAndShortIndent() {
        val result = editSelectedLines(TextFieldValue("\ta\n  b", TextRange(0, 6)), "outdent")
        assertEquals("a\nb", result.text)
    }
    @Test fun cursorAtBeginningIndentsFirstLine() {
        val result = editSelectedLines(TextFieldValue("x", TextRange.Zero), "indent")
        assertEquals("    x", result.text)
        assertEquals(TextRange(4), result.selection)
    }
}
