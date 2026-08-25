package com.pixelpy.editor

import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal enum class NotebookCellType { Code, Markdown }
internal enum class NotebookCellStatus { Idle, Running, Success, Error, Stale }

internal data class NotebookCell(
    val id: String = UUID.randomUUID().toString(),
    val type: NotebookCellType = NotebookCellType.Code,
    val source: String = "",
    val output: String = "",
    val status: NotebookCellStatus = NotebookCellStatus.Idle,
    val durationMillis: Long? = null,
)

internal data class PixelNotebook(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cells: List<NotebookCell> = listOf(NotebookCell()),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

internal class NotebookStore(private val project: File) {
    private val root = File(project, ".pixelpy/notebooks").apply { mkdirs() }

    fun list(): List<PixelNotebook> = root.listFiles { file -> file.extension == "json" }
        .orEmpty()
        .mapNotNull(::read)
        .sortedByDescending(PixelNotebook::updatedAtMillis)

    fun getOrCreate(): PixelNotebook = list().firstOrNull() ?: save(
        PixelNotebook(
            name = "Análisis inicial",
            cells = listOf(
                NotebookCell(
                    type = NotebookCellType.Markdown,
                    source = "# Mi análisis\nDescribe aquí el objetivo del notebook.",
                ),
                NotebookCell(
                    source = "datos = [12, 18, 25, 31]\nprint('Promedio:', sum(datos) / len(datos))",
                ),
            ),
        )
    )

    fun create(name: String): PixelNotebook = save(
        PixelNotebook(name = name.trim().ifBlank { "Notebook sin título" })
    )

    fun importIpynb(source: String, fallbackName: String): PixelNotebook {
        val json = JSONObject(source)
        val cells = json.optJSONArray("cells") ?: JSONArray()
        val imported = buildList {
            repeat(cells.length().coerceAtMost(100)) { index ->
                val cell = cells.getJSONObject(index)
                val rawSource = cell.opt("source")
                val text = when (rawSource) {
                    is JSONArray -> buildString { repeat(rawSource.length()) { append(rawSource.optString(it)) } }
                    else -> rawSource?.toString().orEmpty()
                }
                val type = if (cell.optString("cell_type").lowercase(Locale.ROOT) == "markdown") {
                    NotebookCellType.Markdown
                } else NotebookCellType.Code
                add(NotebookCell(type = type, source = text))
            }
        }.ifEmpty { listOf(NotebookCell()) }
        return save(PixelNotebook(name = fallbackName.substringBeforeLast('.').take(80), cells = imported))
    }

    fun duplicate(notebook: PixelNotebook): PixelNotebook = save(
        notebook.copy(id = UUID.randomUUID().toString(), name = "${notebook.name} copia", updatedAtMillis = 0L)
    )

    fun exportIpynb(notebook: PixelNotebook): String = JSONObject()
        .put("nbformat", 4)
        .put("nbformat_minor", 5)
        .put("metadata", JSONObject().put("language_info", JSONObject().put("name", "python")))
        .put("cells", JSONArray().apply {
            notebook.cells.forEach { cell ->
                put(JSONObject()
                    .put("cell_type", if (cell.type == NotebookCellType.Markdown) "markdown" else "code")
                    .put("metadata", JSONObject())
                    .put("source", cell.source)
                    .apply {
                        if (cell.type == NotebookCellType.Code) {
                            put("execution_count", JSONObject.NULL)
                            put("outputs", JSONArray())
                        }
                    })
            }
        }).toString(2)

    fun save(notebook: PixelNotebook): PixelNotebook {
        val normalized = notebook.copy(
            name = notebook.name.trim().take(80).ifBlank { "Notebook sin título" },
            cells = notebook.cells.take(100).ifEmpty { listOf(NotebookCell()) },
            updatedAtMillis = System.currentTimeMillis(),
        )
        writeUtf8Atomically(File(root, "${normalized.id}.json"), normalized.toJson().toString())
        return normalized
    }

    fun delete(notebook: PixelNotebook): Boolean =
        File(root, "${notebook.id}.json").delete()

    private fun read(file: File): PixelNotebook? = runCatching {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val cells = json.optJSONArray("cells") ?: JSONArray()
        PixelNotebook(
            id = json.getString("id"),
            name = json.optString("name", "Notebook sin título"),
            cells = buildList {
                repeat(cells.length()) { index ->
                    val cell = cells.getJSONObject(index)
                    add(
                        NotebookCell(
                            id = cell.getString("id"),
                            type = enumValueOf(cell.optString("type", NotebookCellType.Code.name)),
                            source = cell.optString("source"),
                            output = cell.optString("output"),
                            status = runCatching {
                                enumValueOf<NotebookCellStatus>(
                                    cell.optString("status", NotebookCellStatus.Idle.name)
                                )
                            }.getOrDefault(NotebookCellStatus.Idle),
                            durationMillis = if (cell.has("durationMillis") && !cell.isNull("durationMillis")) {
                                cell.getLong("durationMillis")
                            } else null,
                        )
                    )
                }
            }.ifEmpty { listOf(NotebookCell()) },
            updatedAtMillis = json.optLong("updatedAtMillis", file.lastModified()),
        )
    }.getOrNull()
}

private fun PixelNotebook.toJson() = JSONObject()
    .put("version", 1)
    .put("id", id)
    .put("name", name)
    .put("updatedAtMillis", updatedAtMillis)
    .put("cells", JSONArray().apply {
        cells.forEach { cell ->
            put(
                JSONObject()
                    .put("id", cell.id)
                    .put("type", cell.type.name)
                    .put("source", cell.source)
                    .put("output", cell.output)
                    .put("status", cell.status.name)
                    .put("durationMillis", cell.durationMillis ?: JSONObject.NULL)
            )
        }
    })

internal fun cumulativeNotebookSource(cells: List<NotebookCell>, throughIndex: Int): String =
    cells.take(throughIndex + 1)
        .filter { it.type == NotebookCellType.Code }
        .joinToString("\n\n") { "# PixelPy cell ${it.id}\n${it.source}" }

internal fun invalidateNotebookCellsAfterEdit(
    cells: List<NotebookCell>,
    editedIndex: Int,
    source: String,
): List<NotebookCell> = cells.mapIndexed { index, cell ->
    when {
        index == editedIndex -> cell.copy(source = source, status = NotebookCellStatus.Idle)
        index > editedIndex && cell.type == NotebookCellType.Code && cell.status != NotebookCellStatus.Idle ->
            cell.copy(status = NotebookCellStatus.Stale)
        else -> cell
    }
}

internal sealed interface NotebookMarkdownBlock {
    data class Heading(val level: Int, val text: String) : NotebookMarkdownBlock
    data class Paragraph(val text: String) : NotebookMarkdownBlock
    data class Bullet(val text: String, val numbered: Boolean = false) : NotebookMarkdownBlock
    data class Quote(val text: String) : NotebookMarkdownBlock
    data class Code(val text: String) : NotebookMarkdownBlock
}

internal fun parseNotebookMarkdown(source: String): List<NotebookMarkdownBlock> {
    val blocks = mutableListOf<NotebookMarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += NotebookMarkdownBlock.Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
        }
    }
    fun flushCode() {
        if (code.isNotEmpty()) {
            blocks += NotebookMarkdownBlock.Code(code.joinToString("\n"))
            code.clear()
        }
    }

    source.lines().forEach { raw ->
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) flushCode() else flushParagraph()
            inCode = !inCode
            return@forEach
        }
        if (inCode) {
            code += raw
            return@forEach
        }
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> flushParagraph()
            Regex("^#{1,6}\\s+").containsMatchIn(trimmed) -> {
                flushParagraph()
                val marks = trimmed.takeWhile { it == '#' }.length
                blocks += NotebookMarkdownBlock.Heading(marks, trimmed.drop(marks).trim())
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks += NotebookMarkdownBlock.Quote(trimmed.removePrefix(">").trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += NotebookMarkdownBlock.Bullet(trimmed.drop(2).trim())
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(trimmed) -> {
                flushParagraph()
                blocks += NotebookMarkdownBlock.Bullet(
                    Regex("^\\d+[.)]\\s+").replace(trimmed, ""),
                    numbered = true,
                )
            }
            else -> paragraph += trimmed
        }
    }
    if (inCode) flushCode() else flushParagraph()
    return blocks
}
