package com.pixelpy.editor

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookStoreTest {
    @Test fun `notebook survives reload with cell output`() {
        val project = Files.createTempDirectory("notebook").toFile()
        val store = NotebookStore(project)
        val saved = store.save(
            PixelNotebook(
                name = "Ventas",
                cells = listOf(
                    NotebookCell(source = "total = 42", output = "42", status = NotebookCellStatus.Success)
                ),
            )
        )

        val restored = NotebookStore(project).list().single()

        assertEquals(saved.id, restored.id)
        assertEquals("Ventas", restored.name)
        assertEquals("42", restored.cells.single().output)
    }

    @Test fun `multiple notebooks remain available after reopening the project`() {
        val project = Files.createTempDirectory("notebook-list").toFile()
        val store = NotebookStore(project)
        val initial = store.getOrCreate()
        val second = store.save(PixelNotebook(name = "Análisis de ventas"))

        val restored = NotebookStore(project).list()

        assertEquals(2, restored.size)
        assertTrue(restored.any { it.id == initial.id })
        assertTrue(restored.any { it.id == second.id && it.name == "Análisis de ventas" })
    }

    @Test fun `cumulative source shares previous code and ignores markdown`() {
        val cells = listOf(
            NotebookCell(source = "x = 4"),
            NotebookCell(type = NotebookCellType.Markdown, source = "# explicación"),
            NotebookCell(source = "print(x * 2)"),
        )

        val source = cumulativeNotebookSource(cells, 2)

        assertTrue("x = 4" in source)
        assertTrue("print(x * 2)" in source)
        assertTrue("# explicación" !in source)
    }

    @Test fun `markdown parser preserves structure`() {
        val blocks = parseNotebookMarkdown(
            "# Informe\n\nTexto principal.\n- Ventas\n> Nota\n```python\nprint('ok')\n```"
        )

        assertTrue(blocks[0] is NotebookMarkdownBlock.Heading)
        assertTrue(blocks.any { it is NotebookMarkdownBlock.Paragraph })
        assertTrue(blocks.any { it is NotebookMarkdownBlock.Bullet })
        assertTrue(blocks.any { it is NotebookMarkdownBlock.Quote })
        assertTrue(blocks.any { it is NotebookMarkdownBlock.Code })
    }

    @Test fun `editing an earlier cell marks later results as stale`() {
        val cells = listOf(
            NotebookCell(source = "x = 1", output = "", status = NotebookCellStatus.Success),
            NotebookCell(type = NotebookCellType.Markdown, source = "Explicación"),
            NotebookCell(source = "print(x)", output = "1", status = NotebookCellStatus.Success),
        )

        val changed = invalidateNotebookCellsAfterEdit(cells, 0, "x = 2")

        assertEquals(NotebookCellStatus.Idle, changed[0].status)
        assertEquals(NotebookCellStatus.Idle, changed[1].status)
        assertEquals(NotebookCellStatus.Stale, changed[2].status)
        assertEquals("1", changed[2].output)
    }
}
