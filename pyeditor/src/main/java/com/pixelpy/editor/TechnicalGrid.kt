package com.pixelpy.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Subtle drafting grid shared by the light and dark reference themes. */
@Composable
internal fun Modifier.technicalGridBackground(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val outline = MaterialTheme.colorScheme.outline
    val dark = background.red < 0.1f
    val line = outline.copy(alpha = if (dark) 0.10f else 0.13f)
    val majorLine = outline.copy(alpha = if (dark) 0.16f else 0.19f)
    val primary = MaterialTheme.colorScheme.primary
    return drawBehind {
        val compact = size.width < 600.dp.toPx()
        val orbit = primary.copy(
            alpha = when {
                compact && dark -> 0.18f
                compact -> 0.14f
                dark -> 0.30f
                else -> 0.24f
            },
        )
        drawRect(background)
        val step = 48.dp.toPx()
        val width = 1.dp.toPx()
        var x = 0f
        var column = 0
        while (x <= size.width) {
            drawLine(if (column % 4 == 0) majorLine else line, Offset(x, 0f), Offset(x, size.height), width)
            x += step
            column++
        }
        var y = 0f
        var row = 0
        while (y <= size.height) {
            drawLine(if (row % 4 == 0) majorLine else line, Offset(0f, y), Offset(size.width, y), width)
            y += step
            row++
        }
        val center = if (compact) {
            Offset(size.width * 1.06f, size.height * 0.11f)
        } else {
            Offset(size.width * 0.88f, size.height * 0.18f)
        }
        val radius = if (compact) 38.dp.toPx() else 94.dp.toPx()
        drawCircle(orbit.copy(alpha = orbit.alpha * 0.62f), radius = radius, center = center, style = Stroke(width))
        drawCircle(orbit, radius = radius * 0.68f, center = center, style = Stroke(width))
        rotate(32f, center) {
            drawOval(
                orbit.copy(alpha = orbit.alpha * 0.82f),
                topLeft = Offset(center.x - radius * 1.52f, center.y - radius * 0.38f),
                size = Size(radius * 3.04f, radius * 0.76f),
                style = Stroke(width),
            )
        }
        drawLine(orbit, Offset(center.x - radius * 1.1f, center.y), Offset(center.x + radius * 1.1f, center.y), width)
        drawLine(orbit, Offset(center.x, center.y - radius * 1.1f), Offset(center.x, center.y + radius * 1.1f), width)
        drawCircle(primary.copy(alpha = if (dark) 0.62f else 0.55f), radius = 4.dp.toPx(), center = center)
        drawCircle(orbit, radius = 3.dp.toPx(), center = Offset(center.x + radius * 1.45f, center.y - radius * 0.82f))

        // A small registration mark anchors the opposite corner like a drafted editorial plate.
        val anchor = Offset(size.width * 0.09f, size.height * 0.88f)
        val anchorRadius = 24.dp.toPx()
        drawCircle(line, anchorRadius, anchor, style = Stroke(width))
        drawLine(line, Offset(anchor.x - anchorRadius * 1.4f, anchor.y), Offset(anchor.x + anchorRadius * 1.4f, anchor.y), width)
        drawLine(line, Offset(anchor.x, anchor.y - anchorRadius * 1.4f), Offset(anchor.x, anchor.y + anchorRadius * 1.4f), width)
    }
}
