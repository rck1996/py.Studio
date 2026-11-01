package com.pixelpy.editor

import java.io.File
import java.io.InputStream

internal const val MAX_CSV_IMPORT_BYTES = 25L * 1024L * 1024L

/** Creates a sanitized, non-overwriting CSV destination inside the selected project. */
internal fun uniqueCsvImportTarget(project: File, displayName: String): File {
    val fileName = displayName.substringAfterLast('/').substringAfterLast('\\').trim()
    require(fileName.endsWith(".csv", ignoreCase = true)) { "Selecciona un archivo .csv" }
    val stem = fileName.dropLast(4)
        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .trim()
        .trim('.')
        .ifBlank { "datos" }
    var target = File(project, "$stem.csv")
    var copy = 2
    while (target.exists()) {
        target = File(project, "$stem ($copy).csv")
        copy++
    }
    require(target.canonicalFile.parentFile == project.canonicalFile) { "Destino CSV inválido" }
    return target
}

/** Copies a selected CSV with a size cap; removes a partial file if the transfer fails. */
internal fun copyCsvToProject(input: InputStream, target: File, maxBytes: Long = MAX_CSV_IMPORT_BYTES): Long {
    require(maxBytes > 0) { "El límite de tamaño debe ser mayor que cero" }
    check(target.parentFile?.isDirectory == true) { "El proyecto ya no está disponible" }
    check(target.createNewFile()) { "Ya existe un archivo con ese nombre" }
    var total = 0L
    try {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "El CSV supera el límite de ${maxBytes / (1024 * 1024)} MB" }
                output.write(buffer, 0, count)
            }
        }
        return total
    } catch (failure: Throwable) {
        target.delete()
        throw failure
    }
}
