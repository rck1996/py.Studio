package com.pixelpy.editor

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {
    @Test
    fun `import target sanitizes provider name and never overwrites existing data`() {
        val project = Files.createTempDirectory("csv-import").toFile()
        try {
            val first = uniqueCsvImportTarget(project, "../ventas?.csv")
            assertEquals("ventas_.csv", first.name)
            first.writeText("old")
            val next = uniqueCsvImportTarget(project, "../ventas?.csv")
            assertEquals("ventas_ (2).csv", next.name)
            assertEquals(project.canonicalFile, next.parentFile?.canonicalFile)
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun `bounded import writes csv contents`() {
        val project = Files.createTempDirectory("csv-copy").toFile()
        try {
            val target = uniqueCsvImportTarget(project, "ventas.csv")
            val copied = copyCsvToProject(ByteArrayInputStream("mes,total\nenero,4".toByteArray()), target)
            assertEquals(17L, copied)
            assertEquals("mes,total\nenero,4", target.readText())
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun `oversized import removes incomplete target`() {
        val project = Files.createTempDirectory("csv-large").toFile()
        try {
            val target = uniqueCsvImportTarget(project, "grande.csv")
            assertThrows(IllegalArgumentException::class.java) {
                copyCsvToProject(ByteArrayInputStream(ByteArray(20)), target, maxBytes = 10)
            }
            assertFalse(target.exists())
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun `non csv is rejected before creating destination`() {
        val project = Files.createTempDirectory("csv-type").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                uniqueCsvImportTarget(project, "script.py")
            }
            assertTrue(project.listFiles().isNullOrEmpty())
        } finally {
            project.deleteRecursively()
        }
    }
}
