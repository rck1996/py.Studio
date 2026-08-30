package com.pixelpy.editor

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBackupStoreTest {
    @Test fun `backup preserves hidden notebook data and retains five`() {
        val root = createTempDirectory("pixelpy-backup").toFile()
        val project = File(root, "Proyecto").apply { mkdirs() }
        File(project, ".pixelpy/notebooks").mkdirs()
        File(project, ".pixelpy/notebooks/a.json").writeText("uno")
        val store = ProjectBackupStore(File(root, "backups"))

        repeat(7) { index -> File(project, "main.py").writeText("print($index)"); store.create(project) }

        assertEquals(5, store.list(project).size)
        File(project, ".pixelpy/notebooks/a.json").writeText("cambio")
        store.restore(project, store.list(project).last())
        assertEquals("uno", File(project, ".pixelpy/notebooks/a.json").readText())
    }

    @Test fun `validated extraction rejects traversal`() {
        val root = createTempDirectory("pixelpy-zip").toFile()
        val zip = File(root, "bad.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("../escape.txt")); it.write(byteArrayOf(1)); it.closeEntry() }

        val result = runCatching { extractValidated(zip, File(root, "target")) }

        assertTrue(result.isFailure)
        assertFalse(File(root, "escape.txt").exists())
    }
}
