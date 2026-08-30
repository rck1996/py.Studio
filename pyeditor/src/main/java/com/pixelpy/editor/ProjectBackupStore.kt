package com.pixelpy.editor

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class ProjectBackup(val file: File, val createdAtMillis: Long, val sizeBytes: Long)

internal class ProjectBackupStore(private val root: File, private val keep: Int = 5) {
    fun list(project: File): List<ProjectBackup> = backupFolder(project).listFiles { file -> file.extension == "zip" }
        ?.sortedByDescending { it.lastModified() }
        ?.map { ProjectBackup(it, it.lastModified(), it.length()) }
        .orEmpty()

    fun create(project: File): ProjectBackup {
        require(project.isDirectory) { "El proyecto no existe" }
        val folder = backupFolder(project).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        var output = File(folder, "$stamp.zip")
        var suffix = 2
        while (output.exists()) { output = File(folder, "$stamp-$suffix.zip"); suffix++ }
        val pending = File(folder, ".${output.nameWithoutExtension}.tmp")
        ZipOutputStream(pending.outputStream().buffered()).use { zip ->
            project.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(project).invariantSeparatorsPath))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        check(pending.renameTo(output)) { "No se pudo finalizar el respaldo" }
        list(project).drop(keep).forEach { it.file.delete() }
        return ProjectBackup(output, output.lastModified(), output.length())
    }

    fun restore(project: File, backup: ProjectBackup) {
        require(backup.file.canonicalFile.parentFile == backupFolder(project).canonicalFile) { "Respaldo ajeno al proyecto" }
        require(backup.file.isFile) { "El respaldo ya no existe" }
        val parent = requireNotNull(project.parentFile)
        val staging = File(parent, ".restore-${UUID.randomUUID()}")
        val rollback = File(parent, ".rollback-${UUID.randomUUID()}")
        try {
            extractValidated(backup.file, staging)
            check(staging.walkTopDown().any { it.isFile }) { "El respaldo está vacío" }
            create(project)
            check(project.renameTo(rollback)) { "No se pudo preparar la restauración" }
            if (!staging.renameTo(project)) {
                rollback.renameTo(project)
                error("No se pudo activar el respaldo")
            }
            rollback.deleteRecursively()
        } finally {
            if (staging.exists()) staging.deleteRecursively()
            if (rollback.exists() && !project.exists()) rollback.renameTo(project)
        }
    }

    private fun backupFolder(project: File): File {
        val safe = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48).ifBlank { "proyecto" }
        return File(root, "$safe-${project.canonicalPath.hashCode().toUInt().toString(16)}")
    }
}

internal fun extractValidated(zipFile: File, target: File) {
    target.mkdirs()
    val rootPath = target.canonicalPath + File.separator
    var total = 0L
    var entries = 0
    ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            entries++
            require(entries <= 5_000) { "El ZIP contiene demasiados archivos" }
            val output = File(target, entry.name)
            require(!entry.name.startsWith("/") && !entry.name.startsWith("\\") && output.canonicalPath.startsWith(rootPath)) {
                "Ruta ZIP inválida"
            }
            if (entry.isDirectory) output.mkdirs() else {
                output.parentFile?.mkdirs()
                output.outputStream().buffered().use { stream ->
                    val buffer = ByteArray(8_192)
                    var read = zip.read(buffer)
                    while (read > 0) {
                        total += read
                        require(total <= 100L * 1_024 * 1_024) { "El ZIP supera 100 MB" }
                        stream.write(buffer, 0, read)
                        read = zip.read(buffer)
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
}
