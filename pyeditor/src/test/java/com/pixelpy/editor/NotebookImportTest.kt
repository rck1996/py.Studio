package com.pixelpy.editor

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportTest {
    @Test
    fun importsCodeMarkdownAndArraySources() {
        val root = Files.createTempDirectory("pixelpy-notebook").toFile()
        val store = NotebookStore(root)
        val notebook = store.importIpynb(
            """{"cells":[{"cell_type":"markdown","source":["# Título\n"]},{"cell_type":"code","source":["print(1)\n"]}]}""",
            "demo.ipynb",
        )
        assertEquals("demo", notebook.name)
        assertEquals(NotebookCellType.Markdown, notebook.cells[0].type)
        assertEquals("print(1)\n", notebook.cells[1].source)
        assertTrue(store.list().isNotEmpty())
    }
}
