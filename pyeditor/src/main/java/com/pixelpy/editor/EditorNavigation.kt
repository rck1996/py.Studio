package com.pixelpy.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

internal fun editorValueAtLine(file: File, line: Int): TextFieldValue {
    val currentSource = file.readText()
    val position = editorLineStartOffset(currentSource, line)
    return TextFieldValue(currentSource, selection = TextRange(position))
}

internal fun editorLineStartOffset(source: String, line: Int): Int {
    if (line <= 1) return 0
    return source
        .lineSequence()
        .take((line - 1).coerceAtLeast(0))
        .sumOf { it.length + 1 }
        .coerceAtMost(source.length)
}
