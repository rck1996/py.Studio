package com.pixelpy.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun editSelectedLines(value: TextFieldValue, action: String): TextFieldValue {
    val start = value.text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(-1)) + 1
    val last = if (!value.selection.collapsed && value.selection.max > start && value.text[value.selection.max - 1] == '\n') value.selection.max - 1 else value.selection.max
    val end = value.text.indexOf('\n', last).let { if (it < 0) value.text.length else it }
    val lines = value.text.substring(start, end).split('\n')
    val uncomment = lines.filter { it.isNotBlank() }.let { it.isNotEmpty() && it.all { line -> line.trimStart().startsWith("#") } }
    val replacement = lines.joinToString("\n") { line ->
        when (action) {
            "indent" -> "    $line"
            "outdent" -> if (line.startsWith('\t')) line.drop(1) else line.drop(line.takeWhile { it == ' ' }.length.coerceAtMost(4))
            "comment" -> {
                val prefix = line.takeWhile { it == ' ' || it == '\t' }
                val body = line.drop(prefix.length)
                when { body.isBlank() -> line; uncomment -> prefix + body.drop(1).removePrefix(" "); else -> prefix + "# " + body }
            }
            else -> line
        }
    }
    val selection = if (value.selection.collapsed) {
        val delta = replacement.length - (end - start)
        TextRange((value.selection.start + delta).coerceIn(start, start + replacement.length))
    } else TextRange(start, start + replacement.length)
    return TextFieldValue(value.text.replaceRange(start, end, replacement), selection)
}
