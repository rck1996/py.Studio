package com.pixelpy.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val NotebookInk = Color(0xFF202124)
private val NotebookPaper = Color(0xFFFFF7E6)
private val NotebookYellow = Color(0xFFFFD83D)
private val NotebookBlue = Color(0xFF74B9FF)
private val NotebookGreen = Color(0xFF7DE39B)
private val NotebookPink = Color(0xFFFF8FB8)

@Composable
internal fun NotebookScreen(
    project: File,
    onBack: () -> Unit,
    onArtifactsChanged: () -> Unit,
) {
    val store = remember(project) { NotebookStore(project) }
    val initialNotebook = remember(project) { store.getOrCreate() }
    var notebooks by remember(project) { mutableStateOf(store.list()) }
    var notebook by remember(project) { mutableStateOf(initialNotebook) }
    var selector by remember { mutableStateOf(false) }
    var newDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var exportNotebook by remember { mutableStateOf<PixelNotebook?>(null) }
    var renameNotebook by remember { mutableStateOf<PixelNotebook?>(null) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var chartDialog by remember { mutableStateOf(false) }
    var notebookQuery by remember { mutableStateOf("") }
    var notebookVariables by remember(project) { mutableStateOf<List<NotebookVariable>>(emptyList()) }
    var runningIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val name = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "importado.ipynb" }
                    ?: "importado.ipynb"
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("No se pudo abrir el archivo")
                }
                val imported = withContext(Dispatchers.IO) { store.importIpynb(content, name) }
                notebook = imported
                notebooks = store.list()
            }.onFailure { importError = "No se pudo importar el notebook: ${it.message}" }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val target = exportNotebook ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(store.exportIpynb(target))
                    } ?: error("No se pudo guardar el archivo")
                }
            }.onFailure { importError = "No se pudo exportar el notebook: ${it.message}" }
        }
    }

    fun persist(next: PixelNotebook) {
        notebook = store.save(next)
        notebooks = store.list()
    }

    fun updateCell(index: Int, transform: (NotebookCell) -> NotebookCell) {
        persist(notebook.copy(cells = notebook.cells.toMutableList().also {
            it[index] = transform(it[index])
        }))
    }

    fun execute(index: Int, runAll: Boolean) {
        if (runningIndex >= 0) return
        val target = if (runAll) {
            notebook.cells.indexOfLast { it.type == NotebookCellType.Code }
        } else index
        if (target < 0 || notebook.cells[target].type != NotebookCellType.Code) return
        runningIndex = target
        updateCell(target) { it.copy(status = NotebookCellStatus.Running, output = "", results = emptyList()) }
        val snapshot = notebook
        val codeIndexes = snapshot.cells.mapIndexedNotNull { cellIndex, cell ->
            cellIndex.takeIf { cell.type == NotebookCellType.Code && cellIndex <= target }
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    PythonRuntimeCoordinator.runExclusive {
                        val value = Python.getInstance().getModule("runner").callAttr(
                            "notebook_run",
                            codeIndexes.map { snapshot.cells[it].source },
                            project.absolutePath,
                            120,
                        )
                        NotebookRuntimeResult(
                            ok = value.callAttr("get", "ok").toBoolean(),
                            cellResults = value.callAttr("get", "results").asList().map { cellResult ->
                                NotebookCellResult(
                                    ok = cellResult.callAttr("get", "ok").toBoolean(),
                                    output = cellResult.callAttr("get", "output").toString(),
                                    durationMillis = cellResult.callAttr("get", "duration_ms").toLong(),
                                    results = parseNotebookResults(
                                        cellResult.callAttr("get", "displays_json").toString(),
                                    ),
                                    files = cellResult.callAttr("get", "files").asList().map { File(it.toString()) },
                                )
                            },
                            files = value.callAttr("get", "files").asList().map { File(it.toString()) },
                            variables = parseNotebookVariables(value.callAttr("get", "variables_json").toString()),
                        )
                    }
                }
            }
            result.onSuccess { execution ->
                val resultByCell = codeIndexes.zip(execution.cellResults).toMap()
                val cells = notebook.cells.mapIndexed { cellIndex, cell ->
                    val cellResult = resultByCell[cellIndex]
                    when {
                        cellResult != null -> cell.copy(
                            output = cellResult.output.ifBlank {
                                if (cellResult.results.isEmpty() && cellResult.files.isEmpty()) {
                                    "✓ Celda terminada sin salida."
                                } else ""
                            },
                            results = cellResult.results + cellResult.files.mapNotNull { file ->
                                file.relativeToOrNull(project)?.let { relative ->
                                    NotebookResult(
                                        kind = NotebookResultKind.File,
                                        title = file.name,
                                        path = relative.invariantSeparatorsPath,
                                    )
                                }
                            },
                            status = if (cellResult.ok) NotebookCellStatus.Success else NotebookCellStatus.Error,
                            durationMillis = cellResult.durationMillis,
                        )
                        cellIndex == target && !execution.ok ->
                            cell.copy(status = NotebookCellStatus.Error, output = "La ejecución se detuvo en una celda anterior.")
                        else -> cell
                    }
                }
                persist(notebook.copy(cells = cells))
                notebookVariables = execution.variables
                if (execution.files.isNotEmpty()) onArtifactsChanged()
            }.onFailure { error ->
                updateCell(target) {
                    it.copy(
                        status = NotebookCellStatus.Error,
                        output = "ERROR DEL NOTEBOOK\n${error::class.simpleName}: ${error.message}",
                    )
                }
            }
            runningIndex = -1
        }
    }

    Column(Modifier.fillMaxSize().background(NotebookPaper).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(3.dp, NotebookInk),
            ) { Text("← VOLVER", color = NotebookInk, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { notebookQuery = ""; selector = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(3.dp, NotebookInk),
                ) { Text(notebook.name.uppercase(), color = NotebookInk, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.width(8.dp))
            NotebookButton("▶ TODO", NotebookGreen, enabled = runningIndex < 0) {
                execute(notebook.cells.lastIndex, true)
            }
        }
        Text(
            "Celdas locales · variables compartidas en orden · ${project.name}",
            Modifier.padding(vertical = 9.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 560.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookButton("IMPORTAR .IPYNB", NotebookBlue, Modifier.weight(1f)) {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                    NotebookButton("EXPORTAR", NotebookYellow, Modifier.weight(1f)) {
                        exportNotebook = notebook
                        exportLauncher.launch("${notebook.name}.ipynb")
                    }
                    NotebookButton("DUPLICAR", NotebookPink, Modifier.weight(1f)) {
                        val copy = store.duplicate(notebook)
                        notebook = copy
                        notebooks = store.list()
                    }
                    NotebookButton("GRÁFICO", NotebookGreen, Modifier.weight(1f)) { chartDialog = true }
                }
            } else {
                Box {
                    NotebookButton("ACCIONES ▾", NotebookYellow) { actionsExpanded = true }
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("IMPORTAR .IPYNB", fontWeight = FontWeight.Black) },
                            onClick = {
                                actionsExpanded = false
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("EXPORTAR", fontWeight = FontWeight.Black) },
                            onClick = {
                                actionsExpanded = false
                                exportNotebook = notebook
                                exportLauncher.launch("${notebook.name}.ipynb")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("DUPLICAR", fontWeight = FontWeight.Black) },
                            onClick = {
                                actionsExpanded = false
                                val copy = store.duplicate(notebook)
                                notebook = copy
                                notebooks = store.list()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("CREAR GRÁFICO", fontWeight = FontWeight.Black) },
                            onClick = { actionsExpanded = false; chartDialog = true },
                        )
                    }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (notebookVariables.isNotEmpty()) {
                item {
                    NotebookVariableInspector(notebookVariables)
                }
            }
            itemsIndexed(notebook.cells, key = { _, cell -> cell.id }) { index, cell ->
                NotebookCellCard(
                    index = index,
                    cell = cell,
                    project = project,
                    running = runningIndex == index,
                    canDelete = notebook.cells.size > 1,
                    onSource = { source ->
                        persist(notebook.copy(cells = invalidateNotebookCellsAfterEdit(notebook.cells, index, source)))
                    },
                    onType = {
                        updateCell(index) {
                            it.copy(
                                type = if (it.type == NotebookCellType.Code) NotebookCellType.Markdown else NotebookCellType.Code,
                                output = "",
                                status = NotebookCellStatus.Idle,
                            )
                        }
                    },
                    onRun = { execute(index, false) },
                    onMove = { direction ->
                        val destination = index + direction
                        if (destination in notebook.cells.indices) {
                            val changed = notebook.cells.toMutableList()
                            val moving = changed.removeAt(index)
                            changed.add(destination, moving)
                            persist(notebook.copy(cells = changed))
                        }
                    },
                    onDelete = {
                        persist(notebook.copy(cells = notebook.cells.filterIndexed { cellIndex, _ -> cellIndex != index }))
                    },
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookButton("＋ CÓDIGO", NotebookBlue, Modifier.weight(1f)) {
                        persist(notebook.copy(cells = notebook.cells + NotebookCell()))
                    }
                    NotebookButton("＋ MARKDOWN", NotebookPink, Modifier.weight(1f)) {
                        persist(notebook.copy(cells = notebook.cells + NotebookCell(type = NotebookCellType.Markdown)))
                    }
                }
            }
        }
    }

    if (selector) {
        AlertDialog(
            onDismissRequest = { selector = false },
            shape = RoundedCornerShape(0.dp),
            containerColor = NotebookPaper,
            title = { Text("MIS NOTEBOOKS", fontWeight = FontWeight.Black) },
            text = {
                val visibleNotebooks = notebooks.filter { it.name.contains(notebookQuery, ignoreCase = true) }
                Column {
                    OutlinedTextField(
                        value = notebookQuery,
                        onValueChange = { notebookQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar notebook") },
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(visibleNotebooks, key = { _, item -> item.id }) { _, item ->
                            val isActive = item.id == notebook.id
                            Column(
                                Modifier.fillMaxWidth().background(if (item.id == notebook.id) NotebookYellow else Color.White)
                                    .border(2.dp, NotebookInk).padding(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Black, color = NotebookInk)
                                        Text("${item.cells.size} celdas", fontSize = 11.sp, color = NotebookInk)
                                    }
                                    if (isActive) {
                                        Text(
                                            "ACTIVO",
                                            modifier = Modifier.border(2.dp, NotebookInk).padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = NotebookInk,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                                Spacer(Modifier.size(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { renameNotebook = item; selector = false }) { Icon(Icons.Outlined.Edit, "Renombrar notebook") }
                                    IconButton(onClick = {
                                        if (notebooks.size > 1 && store.delete(item)) {
                                            notebooks = store.list()
                                            if (item.id == notebook.id) notebook = notebooks.first()
                                        }
                                    }, enabled = notebooks.size > 1) { Icon(Icons.Outlined.Delete, "Eliminar notebook") }
                                    Spacer(Modifier.weight(1f))
                                    NotebookButton(if (isActive) "ABIERTO" else "ABRIR", NotebookBlue, enabled = !isActive) {
                                        notebook = item
                                        selector = false
                                    }
                                }
                            }
                        }
                        if (visibleNotebooks.isEmpty()) {
                            item {
                                Text("No hay notebooks que coincidan.", color = NotebookInk, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                NotebookButton("＋ NUEVO", NotebookGreen) {
                    selector = false
                    newDialog = true
                }
            },
            dismissButton = {
                NotebookButton("CERRAR", Color.White) { selector = false }
            },
        )
    }
    if (chartDialog) {
        ChartBuilderDialog(
            project = project,
            onDismiss = { chartDialog = false },
            onCreate = { source ->
                persist(notebook.copy(cells = notebook.cells + NotebookCell(source = source)))
                chartDialog = false
            },
        )
    }

    if (newDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newDialog = false },
            shape = RoundedCornerShape(0.dp),
            containerColor = NotebookYellow,
            title = { Text("NUEVO NOTEBOOK", fontWeight = FontWeight.Black) },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true) },
            confirmButton = {
                NotebookButton("CREAR", NotebookGreen, enabled = name.isNotBlank()) {
                    notebook = store.create(name)
                    notebooks = store.list()
                    newDialog = false
                }
            },
            dismissButton = { NotebookButton("CANCELAR", Color.White) { newDialog = false } },
        )
    }
    renameNotebook?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameNotebook = null },
            shape = RoundedCornerShape(0.dp),
            containerColor = NotebookYellow,
            title = { Text("RENOMBRAR NOTEBOOK", fontWeight = FontWeight.Black) },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true) },
            confirmButton = { NotebookButton("GUARDAR", NotebookGreen, enabled = name.isNotBlank()) {
                val renamed = store.save(target.copy(name = name))
                notebooks = store.list()
                if (notebook.id == renamed.id) notebook = renamed
                renameNotebook = null
            } },
            dismissButton = { NotebookButton("CANCELAR", Color.White) { renameNotebook = null } },
        )
    }
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            shape = RoundedCornerShape(0.dp),
            containerColor = NotebookYellow,
            title = { Text("IMPORTACIÓN FALLIDA", fontWeight = FontWeight.Black) },
            text = { Text(message) },
            confirmButton = { NotebookButton("CERRAR", NotebookGreen) { importError = null } },
        )
    }
}

@Composable
private fun ChartBuilderDialog(
    project: File,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val csvFiles = remember(project, project.lastModified()) {
        project.walkTopDown().maxDepth(5).filter { it.isFile && it.extension.equals("csv", true) }
            .take(100).toList().sortedBy { it.relativeTo(project).invariantSeparatorsPath.lowercase() }
    }
    var selectedFile by remember(csvFiles) { mutableStateOf(csvFiles.firstOrNull()) }
    val headers = remember(selectedFile, selectedFile?.lastModified()) {
        selectedFile?.let { runCatching { parseCsvPreview(it.readText(Charsets.UTF_8), 1).headers }.getOrDefault(emptyList()) }
            ?: emptyList()
    }
    var xColumn by remember(selectedFile) { mutableStateOf(headers.firstOrNull().orEmpty()) }
    var yColumn by remember(selectedFile) { mutableStateOf(headers.getOrNull(1) ?: headers.firstOrNull().orEmpty()) }
    var kind by remember { mutableStateOf("line") }
    var title by remember { mutableStateOf("Mi gráfico") }
    val disabledReason = when {
        selectedFile == null -> "Selecciona o agrega un archivo CSV."
        headers.isEmpty() -> "No se detectaron encabezados. Usa la primera fila para los nombres de columnas."
        headers.size < 2 -> "Se necesitan al menos dos columnas. Se admiten separadores coma, punto y coma y tabulación."
        xColumn.isBlank() || yColumn.isBlank() -> "Selecciona las columnas para ambos ejes."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(0.dp),
        containerColor = NotebookPaper,
        title = { Text("CREAR GRÁFICO", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (csvFiles.isEmpty()) {
                    Text("Agrega un archivo CSV al proyecto para crear un gráfico.", fontWeight = FontWeight.Bold)
                } else {
                    ChartChoice("ARCHIVO CSV", selectedFile?.relativeTo(project)?.invariantSeparatorsPath.orEmpty(), csvFiles.map { it.relativeTo(project).invariantSeparatorsPath }) { value ->
                        selectedFile = File(project, value)
                    }
                    ChartChoice("EJE X", xColumn, headers) { xColumn = it }
                    ChartChoice("EJE Y", yColumn, headers) { yColumn = it }
                    ChartChoice("TIPO", chartKindLabel(kind), listOf("LÍNEA", "BARRAS", "PUNTOS")) {
                        kind = when (it) { "BARRAS" -> "bar"; "PUNTOS" -> "scatter"; else -> "line" }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Título") },
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                    )
                    Text("Se añadirá una celda editable que genera un SVG local.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    disabledReason?.let {
                        Text(it, color = Color(0xFF9A3412), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            NotebookButton("AÑADIR CELDA", NotebookGreen, enabled = disabledReason == null) {
                val relative = selectedFile!!.relativeTo(project).invariantSeparatorsPath
                onCreate(chartCellSource(relative, xColumn, yColumn, kind, title))
            }
        },
        dismissButton = { NotebookButton("CANCELAR", Color.White, onClick = onDismiss) },
    )
}

@Composable
private fun ChartChoice(label: String, value: String, choices: List<String>, onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(2.dp, NotebookInk),
            ) { Text("${value.ifBlank { "SELECCIONAR" }}  ▾", color = NotebookInk, fontWeight = FontWeight.Black) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onChoice(choice) })
                }
            }
        }
    }
}

private fun chartKindLabel(kind: String) = when (kind) { "bar" -> "BARRAS"; "scatter" -> "PUNTOS"; else -> "LÍNEA" }

internal fun chartCellSource(csvPath: String, x: String, y: String, kind: String, title: String): String {
    val output = "graficos/${File(csvPath).nameWithoutExtension}-${kind}.svg"
    fun quoted(value: String) = JSONObject.quote(value).replace("\\/", "/")
    return """import csv
from pixelpy_chart import chart

with open(${quoted(csvPath)}, encoding="utf-8-sig", newline="") as archivo:
    muestra = archivo.read(4096)
    archivo.seek(0)
    try:
        dialecto = csv.Sniffer().sniff(muestra, delimiters=",;\t")
    except csv.Error:
        dialecto = csv.excel
    filas = list(csv.DictReader(archivo, dialect=dialecto))

chart(filas, ${quoted(x)}, ${quoted(y)}, kind=${quoted(kind)}, title=${quoted(title)}, output=${quoted(output)})"""
}

@Composable
private fun NotebookCellCard(
    index: Int,
    cell: NotebookCell,
    project: File,
    running: Boolean,
    canDelete: Boolean,
    onSource: (String) -> Unit,
    onType: () -> Unit,
    onRun: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (cell.type == NotebookCellType.Code) NotebookBlue else NotebookPink
    var markdownPreview by remember(cell.id) { mutableStateOf(cell.type == NotebookCellType.Markdown) }
    var actionsExpanded by remember(cell.id) { mutableStateOf(false) }
    var outputExpanded by remember(cell.id) { mutableStateOf(true) }
    Surface(
        color = Color.White,
        border = BorderStroke(2.dp, NotebookInk),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(accent).border(2.dp, NotebookInk), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (cell.type == NotebookCellType.Code) "PYTHON" else "MARKDOWN",
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Black,
                )
                Box {
                    IconButton(onClick = { actionsExpanded = true }) { Icon(Icons.Outlined.MoreVert, "Acciones de celda") }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        DropdownMenuItem(text = { Text("Cambiar tipo de celda") }, onClick = { actionsExpanded = false; onType() })
                        DropdownMenuItem(text = { Text("Mover arriba") }, enabled = index > 0, onClick = { actionsExpanded = false; onMove(-1) }, leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) })
                        DropdownMenuItem(text = { Text("Mover abajo") }, onClick = { actionsExpanded = false; onMove(1) }, leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) })
                        DropdownMenuItem(text = { Text("Eliminar") }, enabled = canDelete, onClick = { actionsExpanded = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                    }
                }
            }
            if (cell.type == NotebookCellType.Code) {
                OutlinedTextField(
                    value = cell.source,
                    onValueChange = onSource,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    placeholder = { Text("Escribe Python…") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NotebookButton(if (running) "EJECUTANDO…" else "▶ CELDA", NotebookGreen, enabled = !running, onClick = onRun)
                    Spacer(Modifier.width(10.dp))
                    val state = when (cell.status) {
                        NotebookCellStatus.Idle -> "SIN EJECUTAR"
                        NotebookCellStatus.Running -> "EJECUTANDO"
                        NotebookCellStatus.Success -> "CORRECTO"
                        NotebookCellStatus.Error -> "ERROR"
                        NotebookCellStatus.Stale -> "REQUIERE EJECUTAR"
                    }
                    Text(
                        state + (cell.durationMillis?.let { " · ${it} ms" } ?: ""),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (cell.output.isNotBlank()) {
                    Surface(onClick = { outputExpanded = !outputExpanded }, color = if (cell.status == NotebookCellStatus.Stale) NotebookYellow else NotebookPaper, border = BorderStroke(1.dp, NotebookInk), shape = RoundedCornerShape(0.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("${if (outputExpanded) "▾" else "▸"} SALIDA${if (cell.status == NotebookCellStatus.Stale) " · DESACTUALIZADA" else ""}", Modifier.padding(8.dp, 5.dp), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                    if (outputExpanded) Text(cell.output, Modifier.fillMaxWidth().background(NotebookInk).padding(10.dp), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                cell.results.forEach { result ->
                    NotebookRichResult(result = result, project = project, stale = cell.status == NotebookCellStatus.Stale)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookButton("VISTA", if (markdownPreview) NotebookYellow else Color.White, Modifier.weight(1f)) { markdownPreview = true }
                    NotebookButton("EDITAR", if (!markdownPreview) NotebookYellow else Color.White, Modifier.weight(1f)) { markdownPreview = false }
                }
                if (markdownPreview) {
                    NotebookMarkdownPreview(cell.source)
                } else {
                    OutlinedTextField(
                        value = cell.source,
                        onValueChange = onSource,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        placeholder = { Text("Escribe Markdown…") },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookVariableInspector(variables: List<NotebookVariable>) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        color = NotebookYellow,
        border = BorderStroke(2.dp, NotebookInk),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "▾ VARIABLES" else "▸ VARIABLES", Modifier.weight(1f), fontWeight = FontWeight.Black)
                Text("${variables.size}", fontWeight = FontWeight.Black)
            }
            if (expanded) {
                variables.forEach { variable ->
                    Column(Modifier.fillMaxWidth().background(Color.White).border(1.dp, NotebookInk).padding(8.dp)) {
                        Text(variable.name, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        Text(
                            buildString {
                                append(variable.type)
                                variable.size?.let { append(" · $it elementos") }
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(variable.summary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookRichResult(result: NotebookResult, project: File, stale: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (stale) NotebookYellow else NotebookPaper)
            .border(2.dp, NotebookInk)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            (result.title.ifBlank { result.kind.name }).uppercase(),
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
        )
        when (result.kind) {
            NotebookResultKind.Text -> Text(result.content, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            NotebookResultKind.Json -> {
                val formatted = remember(result.content) {
                    runCatching {
                        val trimmed = result.content.trim()
                        if (trimmed.startsWith("[")) JSONArray(trimmed).toString(2) else JSONObject(trimmed).toString(2)
                    }.getOrDefault(result.content)
                }
                Text(
                    formatted,
                    Modifier.fillMaxWidth().background(NotebookInk).horizontalScroll(rememberScrollState()).padding(9.dp),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            NotebookResultKind.Table -> NotebookInlineTable(result.content)
            NotebookResultKind.File -> {
                val file = remember(project, result.path) {
                    runCatching {
                        File(project, result.path).canonicalFile.takeIf {
                            it.path.startsWith(project.canonicalFile.path + File.separator)
                        }
                    }.getOrNull()
                }
                if (file?.isFile == true) {
                    Text(result.path, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    PublishedPreview(file, previewKind(file), "")
                } else {
                    Text("Resultado no disponible: ${result.path}", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NotebookInlineTable(raw: String) {
    val data = remember(raw) {
        runCatching {
            val json = JSONObject(raw)
            val headersJson = json.optJSONArray("headers") ?: JSONArray()
            val rowsJson = json.optJSONArray("rows") ?: JSONArray()
            val headers = (0 until headersJson.length()).map { headersJson.optString(it) }
            val rows = (0 until rowsJson.length().coerceAtMost(200)).map { rowIndex ->
                val row = rowsJson.optJSONArray(rowIndex) ?: JSONArray()
                (0 until headers.size).map { column -> row.optString(column) }
            }
            CsvPreviewData(headers, rows)
        }.getOrNull()
    }
    if (data == null || data.headers.isEmpty()) {
        Text("No se pudo construir la tabla.", color = Color.Gray)
        return
    }
    Column {
        Text("${data.rows.size} filas · desliza horizontalmente", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            NotebookTableRow(data.headers, header = true)
            data.rows.forEachIndexed { index, row -> NotebookTableRow(row, shaded = index % 2 == 1) }
        }
    }
}

@Composable
private fun NotebookTableRow(values: List<String>, header: Boolean = false, shaded: Boolean = false) {
    Row(Modifier.background(if (header) NotebookYellow else if (shaded) Color(0xFFF1F4F6) else Color.White)) {
        values.forEach { value ->
            Text(
                value.ifEmpty { " " },
                Modifier.width(132.dp).border(1.dp, NotebookInk).padding(8.dp),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (header) FontWeight.Black else FontWeight.Normal,
                fontSize = 10.sp,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun NotebookMarkdownPreview(source: String) {
    val blocks = remember(source) { parseNotebookMarkdown(source) }
    Column(
        Modifier.fillMaxWidth().background(NotebookPaper).border(2.dp, NotebookInk).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (blocks.isEmpty()) Text("Sin contenido Markdown.", color = Color.Gray)
        var orderedItem = 0
        blocks.forEach { block ->
            when (block) {
                is NotebookMarkdownBlock.Heading -> Text(
                    markdownInline(block.text),
                    fontSize = (25 - block.level * 2).coerceAtLeast(15).sp,
                    fontWeight = FontWeight.Black,
                )
                is NotebookMarkdownBlock.Paragraph -> Text(markdownInline(block.text), fontSize = 15.sp)
                is NotebookMarkdownBlock.Bullet -> Row {
                    if (block.numbered) orderedItem++
                    Text(if (block.numbered) "$orderedItem." else "•", fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(markdownInline(block.text), Modifier.weight(1f), fontSize = 15.sp)
                }
                is NotebookMarkdownBlock.Quote -> Text(
                    markdownInline(block.text),
                    Modifier.fillMaxWidth().border(2.dp, NotebookBlue).padding(10.dp),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
                is NotebookMarkdownBlock.Code -> Text(
                    block.text,
                    Modifier.fillMaxWidth().background(NotebookInk).padding(10.dp),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun markdownInline(text: String) = buildAnnotatedString {
    val token = Regex("(\\*\\*.+?\\*\\*|`.+?`|\\*[^*]+?\\*)")
    var cursor = 0
    token.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val value = match.value
        when {
            value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.removeSurrounding("**")) }
            value.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFE0E0E0))) { append(value.removeSurrounding("`")) }
            else -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(value.removeSurrounding("*")) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

@Composable
private fun NotebookButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.border(2.dp, NotebookInk),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = NotebookInk),
        shape = RoundedCornerShape(0.dp),
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

private data class NotebookRuntimeResult(
    val ok: Boolean,
    val cellResults: List<NotebookCellResult>,
    val files: List<File>,
    val variables: List<NotebookVariable>,
)

private data class NotebookCellResult(
    val ok: Boolean,
    val output: String,
    val durationMillis: Long,
    val results: List<NotebookResult>,
    val files: List<File>,
)

private fun parseNotebookResults(raw: String): List<NotebookResult> = runCatching {
    val values = JSONArray(raw)
    buildList {
        repeat(values.length().coerceAtMost(30)) { index ->
            val value = values.optJSONObject(index) ?: return@repeat
            val kind = runCatching { enumValueOf<NotebookResultKind>(value.optString("kind")) }.getOrNull()
                ?: return@repeat
            add(
                NotebookResult(
                    kind = kind,
                    title = value.optString("title"),
                    content = value.optString("content").take(200_000),
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun parseNotebookVariables(raw: String): List<NotebookVariable> = runCatching {
    val values = JSONArray(raw)
    buildList {
        repeat(values.length().coerceAtMost(50)) { index ->
            val value = values.optJSONObject(index) ?: return@repeat
            add(
                NotebookVariable(
                    name = value.optString("name").take(80),
                    type = value.optString("type").take(80),
                    summary = value.optString("summary").take(160),
                    size = if (value.has("size") && !value.isNull("size")) value.optInt("size") else null,
                )
            )
        }
    }
}.getOrDefault(emptyList())
