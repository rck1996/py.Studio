package com.pixelpy.editor

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream

private val Ink = Color(0xFF111111)
private val Outline = Color(0xFFB9B3A8)
private val Yellow = Color(0xFFE84E0F)
private val Pink = Color(0xFFD8664B)
private val Blue = Color(0xFFEEE8DE)
private val Green = Color(0xFFB9B3A8)
private val Success = Color(0xFF6B8B69)
@Composable
private fun referenceColor(color: Color): Color {
    val scheme = MaterialTheme.colorScheme
    return when (color) {
        Yellow -> scheme.primary
        Green -> scheme.secondary
        Pink -> scheme.tertiary
        Blue -> scheme.surfaceVariant
        Outline -> scheme.outline
        Success -> Success
        else -> color
    }
}

@Composable
private fun referenceContentColor(color: Color): Color {
    val scheme = MaterialTheme.colorScheme
    return when (color) {
        Yellow -> scheme.onPrimary
        Green -> scheme.onSecondary
        Pink -> scheme.onTertiary
        Blue -> scheme.onSurfaceVariant
        Success -> Ink
        Ink -> Color(0xFFF6F1E7)
        else -> scheme.onSurface
    }
}
internal enum class Tab { Projects, Editor, Repl, Console }
private data class CodeSymbol(val kind: String, val name: String, val line: Int)
private data class ReplEntry(val command: String, val output: String, val ok: Boolean)
private data class RuntimeResult(val ok: Boolean, val output: String, val files: List<File>, val errorLine: Int?)

class InputBridge(private val onPrompt: (String) -> Unit) {
    private val answers = ArrayBlockingQueue<String>(1)
    @Volatile private var cancelled = false
    fun request(prompt: String): String { onPrompt(prompt); return answers.take() }
    fun submit(answer: String) { answers.offer(answer) }
    fun cancel() { cancelled = true; answers.offer("") }
    fun isCancelled() = cancelled
}

class MainActivity : ComponentActivity() {
    private var requestedAutomationId by mutableStateOf<String?>(null)
    private var requestedAutomationRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedAutomationId = intent.getStringExtra(EXTRA_AUTOMATION_ID)
        requestedAutomationRequest++
        setContent { PixelPy(requestedAutomationId, requestedAutomationRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAutomationId = intent.getStringExtra(EXTRA_AUTOMATION_ID)
        requestedAutomationRequest++
    }
}

@Composable fun PixelPy(requestedAutomationId: String? = null, requestedAutomationRequest: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as PixelPyApp
    val scope = rememberCoroutineScope()
    val transitions = remember { EditorTransitionCoordinator() }
    val projectsRoot = remember { File(context.filesDir, "projects").apply { mkdirs() } }
    val sessionStore = remember { EditorSessionStore(context.applicationContext) }
    val executionSessionStore = remember { ExecutionSessionStore(context.applicationContext) }
    val autosave = remember {
        (context.applicationContext as PixelPyApp).autosaveCoordinator
    }
    val initialSession = remember {
        val fallbackProject = prepareProjects(projectsRoot)
        val fallbackFiles = seed(fallbackProject)
        EditorSessionResolver.resolve(
            projectsRoot,
            fallbackProject,
            fallbackFiles.first(),
            sessionStore.load(),
        )
    }
    val initialContent = remember {
        autosave.latestContent(initialSession.file) ?: initialSession.file.readText()
    }
    var dir by remember { mutableStateOf(initialSession.project) }
    var files by remember {
        mutableStateOf(
            initialSession.project.listFiles { file -> file.isFile && file.extension == "py" }
                ?.sortedBy { it.name }
                ?.ifEmpty { listOf(initialSession.file) }
                ?: listOf(initialSession.file)
        )
    }
    var current by remember { mutableStateOf(initialSession.file) }
    var code by remember {
        mutableStateOf(
            TextFieldValue(
                initialContent,
                selection = TextRange(initialSession.selectionStart, initialSession.selectionEnd),
            )
        )
    }
    val selectionReference = remember { AtomicReference(code.selection) }
    var lastRunFile by remember { mutableStateOf(current.name) }
    var lastRunProject by remember { mutableStateOf(dir.absolutePath) }
    var lastRunSource by remember { mutableStateOf(code.text) }
    var output by remember { mutableStateOf("Pulsa EJECUTAR para ver el resultado.") }
    var generated by remember { mutableStateOf<List<File>>(emptyList()) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var running by remember { mutableStateOf(false) }
    val runtimeBusy by PythonRuntimeCoordinator.busy.collectAsState()
    var lastErrorLine by remember { mutableStateOf<Int?>(null) }
    var tab by remember { mutableStateOf(Tab.valueOf(initialSession.tab)) }
    var showAutomations by rememberSaveable { mutableStateOf(requestedAutomationId != null) }
    var showNotebook by rememberSaveable { mutableStateOf(false) }
    var automationFocusId by remember { mutableStateOf(requestedAutomationId) }
    var automationFocusRequest by remember { mutableIntStateOf(requestedAutomationRequest) }
    var newDialog by remember { mutableStateOf(false) }
    var newProjectDialog by remember { mutableStateOf(false) }
    var resourceVersion by remember { mutableIntStateOf(0) }
    var resourceImportFolder by remember { mutableStateOf("") }
    var projectVersion by remember { mutableIntStateOf(0) }
    val backupStore = remember { ProjectBackupStore(File(context.filesDir, "project-backups")) }
    var showBackups by remember { mutableStateOf(false) }
    var backupVersion by remember { mutableIntStateOf(0) }
    var replEntries by remember { mutableStateOf<List<ReplEntry>>(emptyList()) }
    var inputBridge by remember { mutableStateOf<InputBridge?>(null) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val preferences = remember { context.getSharedPreferences("pixelpy", 0) }
    var showPreferences by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", true)) }
    var highContrast by remember { mutableStateOf(preferences.getBoolean("high_contrast", false)) }
    var interfaceScale by remember { mutableFloatStateOf(preferences.getFloat("interface_scale", 1f)) }
    var executionSessions by remember { mutableStateOf(executionSessionStore.list()) }
    val saveStates by autosave.states.collectAsState()
    val saveStatus = saveStates[runCatching { current.canonicalPath }.getOrDefault(current.path)]
        ?: SaveStatus.Saved
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(requestedAutomationId, requestedAutomationRequest) {
        automationFocusId = requestedAutomationId
        if (requestedAutomationId != null) showAutomations = true
        automationFocusRequest = requestedAutomationRequest
    }

    LaunchedEffect(Unit) {
        autosave.registerFile(current, initialContent)
    }

    fun persistSession(
        project: File = dir,
        file: File = current,
        activeTab: Tab = tab,
        selection: TextRange = selectionReference.get(),
    ) {
        sessionStore.save(
            projectsRoot,
            project,
            file,
            activeTab.name,
            selection.start,
            selection.end,
        )
    }

    suspend fun flushPendingSave(file: File = current): Boolean {
        autosave.flushPendingSave(file)
        val saved = autosave.status(file) != SaveStatus.Error
        if (!saved) {
            android.widget.Toast.makeText(
                context,
                "No se pudo guardar ${file.name}",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        return saved
    }

    fun transition(block: suspend () -> Unit) {
        transitions.launch(scope, block)
    }

    suspend fun openNow(
        file: File,
        destination: Tab = Tab.Editor,
        selection: TextRange = TextRange.Zero,
    ) {
        val opened = file.canonicalFile
        val source = withContext(Dispatchers.IO) {
            autosave.latestContent(opened) ?: opened.readText()
        }
        autosave.registerFile(opened, source)
        current = opened
        code = TextFieldValue(
            source,
            selection = TextRange(
                selection.start.coerceIn(0, source.length),
                selection.end.coerceIn(0, source.length),
            ),
        )
        selectionReference.set(code.selection)
        tab = destination
    }

    suspend fun switchProjectNow(project: File, destination: Tab = Tab.Projects) {
        val openedProject = project.canonicalFile
        val projectFiles = withContext(Dispatchers.IO) {
            openedProject.listFiles { file -> file.isFile && file.extension == "py" }
                ?.sortedBy { it.name }
                ?.ifEmpty {
                    val main = File(openedProject, "main.py")
                    writeUtf8Atomically(main, "print(\"Nuevo proyecto py.Studio\")\n")
                    listOf(main)
                }
                ?: emptyList()
        }
        if (projectFiles.isNotEmpty()) {
            dir = openedProject
            files = projectFiles
            openNow(projectFiles.first(), destination)
        }
    }

    fun open(file: File) {
        transition {
            if (file.canonicalFile == current.canonicalFile) {
                tab = Tab.Editor
                return@transition
            }
            if (!flushPendingSave(current)) return@transition
            openNow(file)
        }
    }

    fun switchProject(project: File) {
        transition {
            if (project.canonicalFile == dir.canonicalFile) return@transition
            if (!flushPendingSave(current)) return@transition
            switchProjectNow(project)
        }
    }

    LaunchedEffect(dir.path, current.path, tab, code.selection) {
        delay(650)
        persistSession()
    }

    val latestProject by rememberUpdatedState(dir)
    val latestFile by rememberUpdatedState(current)
    val latestTab by rememberUpdatedState(tab)
    val latestBridge by rememberUpdatedState(inputBridge)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                sessionStore.save(
                    projectsRoot,
                    latestProject,
                    latestFile,
                    latestTab.name,
                    selectionReference.get().start,
                    selectionReference.get().end,
                )
                autosave.flushAsync(latestFile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestBridge?.cancel()
            sessionStore.save(
                projectsRoot,
                latestProject,
                latestFile,
                latestTab.name,
                selectionReference.get().start,
                selectionReference.get().end,
            )
            autosave.flushAsync(latestFile)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = exportFile
        exportFile = null
        if (uri != null && source != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        source.inputStream().use { it.copyTo(outputStream) }
                    }
                }
            }.onSuccess {
                android.widget.Toast.makeText(context, "Archivo guardado", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "No se pudo guardar", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transition {
            if (!flushPendingSave(current)) return@transition
            runCatching {
                val target = withContext(Dispatchers.IO) {
                    val requested = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                        ?.takeIf { it.endsWith(".py") } ?: "importado.py"
                    var imported = File(dir, requested)
                    var number = 2
                    while (imported.exists()) {
                        imported = File(dir, requested.removeSuffix(".py") + "_$number.py")
                        number++
                    }
                    val importedSource = context.contentResolver.openInputStream(uri)!!.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    }
                    writeUtf8Atomically(imported, importedSource)
                    imported
                }
                files = dir.listFiles { file -> file.extension == "py" }!!.sortedBy { it.name }
                openNow(target)
            }.onFailure {
                android.widget.Toast.makeText(context, "No se pudo importar", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val resourceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val requested = contentName(context, uri).ifBlank { "recurso" }
                    val importFolder = resourceImportFolder
                    var target = safeResourceDestination(dir, importFolder, requested)
                    var number = 2
                    while (target.exists()) {
                        target = safeResourceDestination(
                            dir,
                            importFolder,
                            requested.substringBeforeLast('.', requested) + "_$number" +
                                requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" },
                        )
                        number++
                    }
                    target.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)!!.use { inputStream ->
                        target.outputStream().use { inputStream.copyTo(it) }
                    }
                    target
                }
            }.onSuccess { target ->
                resourceVersion++
                android.widget.Toast.makeText(context, "${target.name} añadido al proyecto", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "No se pudo importar el recurso", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val projectImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transition {
            if (!flushPendingSave(current)) return@transition
            runCatching {
                val target = withContext(Dispatchers.IO) {
                    val base = contentName(context, uri).removeSuffix(".zip").ifBlank { "Proyecto importado" }
                    var imported = File(projectsRoot, base)
                    var number = 2
                    while (imported.exists()) { imported = File(projectsRoot, "$base $number"); number++ }
                    imported.mkdirs()
                    context.contentResolver.openInputStream(uri)!!.use { unzipProject(it, imported) }
                    if (imported.listFiles().isNullOrEmpty()) throw IllegalArgumentException("ZIP vacío")
                    imported
                }
                projectVersion++
                switchProjectNow(target)
            }.onFailure {
                android.widget.Toast.makeText(context, "No se pudo importar el proyecto ZIP", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun executeSnapshot(
        executedFile: String,
        executedSource: String,
        executedProject: String,
        debug: Boolean,
        bridge: InputBridge,
        startedAtMillis: Long,
        parameters: Map<String, String>,
    ) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    PythonRuntimeCoordinator.runExclusive {
                        withPythonParameters(parameters) {
                            val module = Python.getInstance().getModule("runner")
                            val issues = module.callAttr("analyze", executedSource, executedFile).asList().map { it.toString() }
                            val errors = issues.filter { it.startsWith("ERROR|") }
                            if (errors.isNotEmpty()) {
                                val line = errors.first().split('|', limit = 3).getOrNull(1)?.toIntOrNull()
                                RuntimeResult(false, "ANÁLISIS PREVIO\n\n" + issues.joinToString("\n") { issue -> val parts = issue.split('|', limit = 3); "${parts[0]} · línea ${parts[1]}: ${parts[2]}" }, emptyList(), line)
                            } else {
                                val value = module.callAttr("execute", executedSource, "", executedProject, bridge, 120, debug, executedFile)
                                val warnings = issues.joinToString("\n") { issue -> val parts = issue.split('|', limit = 3); "⚠ Línea ${parts[1]}: ${parts[2]}" }
                                val trace = value.callAttr("get", "trace").asList().joinToString("\n") { it.toString() }
                                RuntimeResult(value.callAttr("get", "ok").toBoolean(), (if (warnings.isBlank()) "" else "$warnings\n\n") + value.callAttr("get", "output").toString() + if (trace.isBlank()) "" else "\n\nDEPURACIÓN · LÍNEAS Y VARIABLES\n$trace", value.callAttr("get", "files").asList().map { File(it.toString()) }, value.callAttr("get", "error_line").toInt().takeIf { it > 0 })
                            }
                        }
                    }
                }
                success = result.ok; output = result.output.ifBlank { "✓ Programa terminado sin salida." }; generated = result.files; lastErrorLine = result.errorLine; if (result.files.isNotEmpty()) resourceVersion++
                executionSessions = withContext(Dispatchers.IO) {
                    executionSessionStore.record(
                        projectPath = runCatching {
                            File(executedProject).canonicalFile.relativeTo(projectsRoot.canonicalFile).invariantSeparatorsPath
                        }.getOrDefault(File(executedProject).name),
                        scriptName = executedFile,
                        source = executedSource,
                        output = output,
                        success = result.ok,
                        startedAtMillis = startedAtMillis,
                        finishedAtMillis = System.currentTimeMillis(),
                        generatedFiles = result.files,
                    )
                    executionSessionStore.list()
                }
            } catch (error: Exception) {
                success = false; lastErrorLine = null; output = "ERROR DEL RUNTIME\n${error::class.simpleName}: ${error.message}"
                executionSessions = withContext(Dispatchers.IO) {
                    executionSessionStore.record(
                        projectPath = File(executedProject).name,
                        scriptName = executedFile,
                        source = executedSource,
                        output = output,
                        success = false,
                        startedAtMillis = startedAtMillis,
                        finishedAtMillis = System.currentTimeMillis(),
                        generatedFiles = emptyList(),
                    )
                    executionSessionStore.list()
                }
            } finally {
                running = false
                inputBridge = null
            }
        }
    }

    fun runCode(debug: Boolean = false, parameters: Map<String, String> = emptyMap()) {
        if (running || runtimeBusy) {
            android.widget.Toast.makeText(context, "El runtime Python está ocupado", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        transition {
            if (running || runtimeBusy) return@transition
            val executionFile = current.canonicalFile
            if (!flushPendingSave(executionFile)) return@transition
            if (running || runtimeBusy) return@transition
            val executedSource = withContext(Dispatchers.IO) {
                backupFile(executionFile)
                executionFile.readText()
            }
            val executedFile = executionFile.name
            val executedProject = requireNotNull(executionFile.parentFile).absolutePath
            running = true
            generated = emptyList()
            tab = Tab.Console
            lastRunFile = executedFile; lastRunProject = executedProject; lastRunSource = executedSource
            val bridge = InputBridge { question -> scope.launch(Dispatchers.Main) { pendingPrompt = question.ifBlank { "Python solicita un valor" } } }
            inputBridge = bridge
            executeSnapshot(executedFile, executedSource, executedProject, debug, bridge, System.currentTimeMillis(), parameters)
        }
    }
    fun stopCode() { inputBridge?.cancel(); pendingPrompt = null; output = "Deteniendo ejecución…" }

    fun shareNow(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartir ${file.name}"))
    }

    fun shareFile(file: File) {
        transition {
            if (!ensureFileReadyForPhysicalRead(file, current, ::flushPendingSave)) return@transition
            shareNow(file)
        }
    }

    fun viewGeneratedFile(file: File) {
        val relative = runCatching {
            file.canonicalFile.relativeTo(projectsRoot.canonicalFile).invariantSeparatorsPath
        }.getOrNull() ?: run {
            android.widget.Toast.makeText(context, "El resultado no pertenece al proyecto", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(Intent(context, PublishedResultActivity::class.java).apply {
            putExtra(EXTRA_PROJECT_RESULT_PATH, "projects/$relative")
        })
    }

    LaunchedEffect(showAutomations) {
        if (showAutomations && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? android.app.Activity)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
            }
        }
    }

    fun viewSessionArtifact(session: ExecutionSession, relativePath: String) {
        context.startActivity(Intent(context, PublishedResultActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_SESSION_ARTIFACT_PATH, relativePath)
        })
    }

    fun testCurrentAsAutomation() {
        transition {
            if (!flushPendingSave(current)) return@transition
            val root = projectsRoot.canonicalFile
            val projectPath = runCatching { dir.canonicalFile.relativeTo(root).invariantSeparatorsPath }.getOrNull()
            val scriptPath = runCatching { current.canonicalFile.relativeTo(dir.canonicalFile).invariantSeparatorsPath }.getOrNull()
            val automation = app.automationRepository.automations.value.firstOrNull {
                it.enabled && it.projectPath == projectPath && it.scriptPath == scriptPath
            }
            if (automation == null) {
                showAutomations = true
                automationFocusId = null
                android.widget.Toast.makeText(
                    context,
                    "Crea una automatización para este script y luego pruébala.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            } else {
                app.automationScheduler.runNow(automation.id, AutomationRunOrigin.EditorTest)
                automationFocusId = automation.id
                showAutomations = true
                automationFocusRequest++
                android.widget.Toast.makeText(context, "Prueba enviada al mismo entorno del Worker", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = showNotebook || showAutomations || tab != Tab.Projects) {
        when {
            showNotebook -> showNotebook = false
            showAutomations -> showAutomations = false
            tab == Tab.Console -> tab = Tab.Editor
            else -> tab = Tab.Projects
        }
    }
    val navigationTab = if (showNotebook || showAutomations) Tab.Projects else tab
    val scheme = if (darkMode) darkColorScheme(
        primary = Yellow, onPrimary = Color(0xFF111111),
        secondary = Green, onSecondary = Color(0xFF111111),
        tertiary = Pink, onTertiary = Color(0xFF111111),
        primaryContainer = Color(0xFF462116), onPrimaryContainer = Color(0xFFF8DED3),
        secondaryContainer = Color(0xFF34312C), onSecondaryContainer = Color(0xFFF6F1E7),
        tertiaryContainer = Color(0xFF39241E), onTertiaryContainer = Color(0xFFF6F1E7),
        background = Color(0xFF111111), surface = Color(0xFF191919), surfaceVariant = Color(0xFF292724),
        onBackground = Color(0xFFF6F1E7), onSurface = Color(0xFFF6F1E7),
        onSurfaceVariant = Color(0xFFF6F1E7), outline = Color(0xFF5B564F),
    ) else lightColorScheme(
        primary = Yellow, onPrimary = Color(0xFF111111),
        secondary = Green, onSecondary = Color(0xFF111111),
        tertiary = Color(0xFFEEE8DE), onTertiary = Color(0xFF111111),
        primaryContainer = Color(0xFFF7D4C7), onPrimaryContainer = Color(0xFF541F0D),
        secondaryContainer = Color(0xFFE9E3D9), onSecondaryContainer = Color(0xFF24211D),
        tertiaryContainer = Color(0xFFEEE8DE), onTertiaryContainer = Color(0xFF111111),
        background = if (highContrast) Color.White else Color(0xFFF6F1E7),
        surface = if (highContrast) Color.White else Color(0xFFF6F1E7),
        surfaceVariant = Color(0xFFEEE8DE),
        onBackground = Color(0xFF111111), onSurface = Color(0xFF111111),
        onSurfaceVariant = Color(0xFF111111), outline = Outline,
    )
    SideEffect {
        (context as? ComponentActivity)?.window?.let { window ->
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            val view = window.decorView
            var flags = view.systemUiVisibility
            flags = if (darkMode) flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() else flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = if (darkMode) flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() else flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            view.systemUiVisibility = flags
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, LocalDensity.current.fontScale * interfaceScale)) {
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            modifier = Modifier.testTag("pixelpy-root").technicalGridBackground(),
            containerColor = Color.Transparent,
            contentColor = scheme.onBackground,
            topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.pixelpy_brand_mark), "py.Studio", Modifier.size(34.dp), tint = Color.Unspecified)
                Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(buildAnnotatedString { append("py"); withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(".") }; append("Studio") }, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.4).sp); Text(current.name, Modifier.testTag("current-file-name"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1) }
                if (!showAutomations && !showNotebook && (tab == Tab.Editor || running)) {
                    BrutalButton(if (running) "■ DETENER" else "▶ EJECUTAR", if (running) Pink else Green, Modifier.testTag("run-button"), onClick = { if (running) stopCode() else runCode(false) })
                } else {
                    Surface(color = referenceColor(Blue), contentColor = referenceContentColor(Blue), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, referenceColor(Outline))) {
                        Text(if (showNotebook) "NOTEBOOK" else if (showAutomations) "AUTOMATIZACIONES" else when (tab) { Tab.Projects -> "PROYECTOS"; Tab.Repl -> "REPL"; Tab.Console -> "CONSOLA"; Tab.Editor -> "EDITOR" }, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        }, bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp)) {
                Nav(Tab.Projects, navigationTab, "PROYECTOS", Icons.Outlined.Folder) { showAutomations = false; showNotebook = false; tab = it }
                Nav(Tab.Editor, navigationTab, "EDITOR", Icons.Outlined.Code) { showAutomations = false; showNotebook = false; tab = it }
                Nav(Tab.Repl, navigationTab, "REPL", Icons.Outlined.DataObject) { showAutomations = false; showNotebook = false; tab = it }
                Nav(Tab.Console, navigationTab, "CONSOLA", Icons.Outlined.Terminal) { showAutomations = false; showNotebook = false; tab = it }
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (showNotebook) {
                    NotebookScreen(
                        project = dir,
                        onBack = { showNotebook = false },
                        onArtifactsChanged = { resourceVersion++ },
                    )
                } else if (showAutomations) {
                    AutomationScreen(
                        projectsRoot = projectsRoot,
                        repository = app.automationRepository,
                        scheduler = app.automationScheduler,
                        currentProject = dir,
                        currentScript = current,
                        flushCurrent = { flushPendingSave(current) },
                        requestedAutomationId = automationFocusId,
                        onBack = { showAutomations = false },
                        requestedAutomationRequest = automationFocusRequest,
                    )
                } else when (tab) {
                    Tab.Projects -> Projects(remember(projectVersion) { projectsRoot.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList() }, dir, files, remember(dir, resourceVersion) { dir.walkTopDown().filter { f -> f.isFile && f.extension != "py" && !f.invariantSeparatorsPath.contains("/.trash/") && !f.invariantSeparatorsPath.contains("/.versions/") }.sortedBy { it.relativeTo(dir).invariantSeparatorsPath }.toList() }, current, onProject = ::switchProject, onNewProject = { newProjectDialog = true }, onImportProject = { projectImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, onBackups = { showBackups = true }, onExportProject = { project ->
                        transition {
                            val zip = File(context.cacheDir, project.name + ".zip")
                            if (!exportProjectToZip(project, dir, current, zip, ::flushPendingSave)) return@transition
                            exportFile = zip
                            saveLauncher.launch(zip.name)
                        }
                    }, onOpen = ::open, onNew = { newDialog = true }, onImport = { importLauncher.launch(arrayOf("text/x-python", "text/plain", "*/*")) }, onImportResource = { folder ->
                        resourceImportFolder = folder
                        resourceLauncher.launch(arrayOf("*/*"))
                    }, onDelete = { file ->
                        transition {
                            if (file.canonicalFile == current.canonicalFile && !flushPendingSave(current)) return@transition
                            if (files.size > 1) {
                                val remaining = withContext(Dispatchers.IO) {
                                    moveToTrash(file)
                                    dir.listFiles { candidate -> candidate.extension == "py" }
                                        ?.sortedBy { it.name } ?: emptyList()
                                }
                                files = remaining
                                if (file.canonicalFile == current.canonicalFile && remaining.isNotEmpty()) {
                                    openNow(remaining.first())
                                }
                            }
                        }
                    }, onDeleteResource = { file -> if (moveToTrash(file, dir)) resourceVersion++ }, onTrashChanged = { files = dir.listFiles { f -> f.extension == "py" }?.sortedBy { it.name } ?: emptyList(); resourceVersion++ }, onRename = { file, raw ->
                        transition {
                            val renamingCurrent = file.canonicalFile == current.canonicalFile
                            if (renamingCurrent && !flushPendingSave(current)) return@transition
                            val renamed = File(dir, raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").removeSuffix(".py") + ".py")
                            val renamedFiles = withContext(Dispatchers.IO) {
                                if (!renamed.exists() && file.renameTo(renamed)) {
                                    dir.listFiles { candidate -> candidate.extension == "py" }!!.sortedBy { it.name }
                                } else null
                            }
                            if (renamedFiles != null) {
                                files = renamedFiles
                                if (renamingCurrent) {
                                    current = renamed
                                    autosave.registerFile(renamed, code.text)
                                }
                            } else android.widget.Toast.makeText(context, "No se pudo renombrar", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, onDuplicate = { file ->
                        transition {
                            if (!ensureFileReadyForPhysicalRead(file, current, ::flushPendingSave)) return@transition
                            files = withContext(Dispatchers.IO) {
                                var copy = File(dir, file.nameWithoutExtension + "_copia.py")
                                var number = 2
                                while (copy.exists()) {
                                    copy = File(dir, file.nameWithoutExtension + "_copia$number.py")
                                    number++
                                }
                                file.copyTo(copy)
                                dir.listFiles { candidate -> candidate.extension == "py" }!!.sortedBy { it.name }
                            }
                        }
                    }, onShare = ::shareFile, onAutomations = { showAutomations = true }, onNotebook = { showNotebook = true }, onPreferences = { showPreferences = true }, onPreviewResource = ::viewGeneratedFile, onResourcePathsChanged = { changes ->
                        val projectPath = dir.relativeTo(projectsRoot).invariantSeparatorsPath
                        val changed = changes.entries.sumOf { (oldPath, newPath) ->
                            app.automationRepository.updateHighlightedResourcePath(projectPath, oldPath, newPath)
                        }
                        resourceVersion++
                        if (changed > 0) android.widget.Toast.makeText(
                            context,
                            "$changed automatización(es) actualizada(s)",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }, onTemplate = { template ->
                        transition {
                            if (!flushPendingSave(current)) return@transition
                            val created = withContext(Dispatchers.IO) {
                                var target = File(dir, template.fileName)
                                var suffix = 2
                                while (target.exists()) { target = File(dir, "${template.fileName.removeSuffix(".py")}_$suffix.py"); suffix++ }
                                writeUtf8Atomically(target, template.source)
                                target
                            }
                            files = dir.listFiles { candidate -> candidate.extension == "py" }!!.sortedBy { it.name }
                            openNow(created)
                        }
                    })
                    Tab.Editor -> Editor(files, current, code, saveStatus, onOpen = ::open, onDebug = { runCode(true) }, onTestAutomation = ::testCurrentAsAutomation, onCode = { next ->
                        val textChanged = next.text != code.text
                        selectionReference.set(next.selection)
                        code = next
                        if (textChanged) autosave.onEdit(current, next.text)
                    })
                    Tab.Repl -> ReplScreen(dir, replEntries, runtimeBusy) { replEntries = it }
                    Tab.Console -> Console(lastRunFile, lastRunSource, output, generated, executionSessions, success, running, lastErrorLine, onBack = { tab = Tab.Editor }, onGoToLine = { line ->
                        transition {
                            if (!flushPendingSave(current)) return@transition
                            val target = File(lastRunProject, lastRunFile).canonicalFile
                            val loaded: Triple<File, List<File>, TextFieldValue>? = withContext(Dispatchers.IO) {
                                if (!target.exists()) null else {
                                    val targetProject = requireNotNull(target.parentFile)
                                    Triple(
                                        targetProject,
                                        targetProject.listFiles { file -> file.extension == "py" }
                                            ?.sortedBy { it.name } ?: emptyList(),
                                        editorValueAtLine(target, line),
                                    )
                                }
                            }
                            if (loaded != null) {
                                val (targetProject, targetFiles, value) = loaded
                                if (targetProject.canonicalFile != dir.canonicalFile) {
                                    dir = targetProject
                                    files = targetFiles
                                }
                                autosave.registerFile(target, value.text)
                                current = target
                                code = value
                                selectionReference.set(value.selection)
                                tab = Tab.Editor
                            } else {
                                android.widget.Toast.makeText(context, "El archivo ejecutado ya no existe", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, onRun = { runCode() }, onRunWithParameters = { runCode(parameters = it) }, onView = ::viewGeneratedFile, onSave = { exportFile = it; saveLauncher.launch(it.name) }, onShare = ::shareFile,
                        onOpenSessionArtifact = ::viewSessionArtifact,
                        onPinSession = { session ->
                            executionSessionStore.setPinned(session.id, !session.pinned)
                            executionSessions = executionSessionStore.list()
                        },
                        onDeleteSession = { session ->
                            executionSessionStore.delete(session.id)
                            executionSessions = executionSessionStore.list()
                        },
                        onExportSession = { session ->
                            scope.launch {
                                val zip = withContext(Dispatchers.IO) {
                                    executionSessionStore.export(
                                        session,
                                        File(context.cacheDir, "py.Studio-${session.scriptName.removeSuffix(".py")}-${session.id.take(8)}.zip"),
                                    )
                                }
                                exportFile = zip
                                saveLauncher.launch(zip.name)
                            }
                        },
                    )
                }
            }
        }
    }
    }
    if (newDialog) NewFileDialog(onDismiss = { newDialog = false }) { raw ->
        transition {
            if (!flushPendingSave(current)) return@transition
            val name = raw.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "script" } + ".py"
            val file = withContext(Dispatchers.IO) {
                File(dir, name).also { target ->
                    if (!target.exists()) writeUtf8Atomically(target, "# $name\nprint(\"Hola desde py.Studio\")\n")
                }
            }
            files = dir.listFiles { candidate -> candidate.extension == "py" }!!.sortedBy { it.name }
            openNow(file)
            newDialog = false
        }
    }
    if (newProjectDialog) NewProjectDialog(onDismiss = { newProjectDialog = false }) { raw ->
        transition {
            if (!flushPendingSave(current)) return@transition
            val (project, main) = withContext(Dispatchers.IO) {
                val safe = raw.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_").ifBlank { "Nuevo proyecto" }
                var created = File(projectsRoot, safe)
                var number = 2
                while (created.exists()) { created = File(projectsRoot, "$safe $number"); number++ }
                created.mkdirs()
                val entry = File(created, "main.py")
                writeUtf8Atomically(entry, "# ${created.name}\nprint(\"Hola desde ${created.name}\")\n")
                created to entry
            }
            dir = project
            files = listOf(main)
            openNow(main, Tab.Projects)
            projectVersion++
            newProjectDialog = false
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, LocalDensity.current.fontScale * interfaceScale)) {
        MaterialTheme(colorScheme = scheme) {
            if (showBackups) ProjectBackupsDialog(
                project = dir,
                backups = remember(dir, backupVersion) { backupStore.list(dir) },
                onDismiss = { showBackups = false },
                onCreate = {
                    transition {
                        if (!flushPendingSave(current)) return@transition
                        runCatching { withContext(Dispatchers.IO) { backupStore.create(dir) } }
                            .onSuccess { backupVersion++; android.widget.Toast.makeText(context, "Respaldo creado", android.widget.Toast.LENGTH_SHORT).show() }
                            .onFailure { android.widget.Toast.makeText(context, "No se pudo crear el respaldo", android.widget.Toast.LENGTH_LONG).show() }
                    }
                },
                onRestore = { backup ->
                    transition {
                        if (!flushPendingSave(current)) return@transition
                        runCatching { withContext(Dispatchers.IO) { backupStore.restore(dir, backup) } }
                            .onSuccess { backupVersion++; resourceVersion++; switchProjectNow(dir); android.widget.Toast.makeText(context, "Proyecto restaurado", android.widget.Toast.LENGTH_SHORT).show() }
                            .onFailure { android.widget.Toast.makeText(context, "No se pudo restaurar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
                    }
                },
            )
            pendingPrompt?.let { question ->
                InputDialog(question, onCancel = ::stopCode, onSubmit = { answer -> pendingPrompt = null; inputBridge?.submit(answer) })
            }
            if (showPreferences) PreferencesDialog(darkMode, highContrast, interfaceScale, onDismiss = { showPreferences = false }) { dark, contrast, scale ->
                darkMode = dark
                highContrast = contrast
                interfaceScale = scale
                preferences.edit().putBoolean("dark_mode", dark).putBoolean("high_contrast", contrast).putFloat("interface_scale", scale).apply()
            }
        }
    }
}

@Composable private fun RowScope.Nav(value: Tab, selected: Tab, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (Tab) -> Unit) {
    val active = value == selected
    Column(Modifier.weight(1f).fillMaxHeight().padding(5.dp).testTag("nav-${value.name.lowercase()}").clickable { onClick(value) }.background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(21.dp), tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f))
        Text(label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.6.sp)
    }
}

private fun interface ResourceImporter {
    fun importInto(folder: String)
    operator fun invoke(folder: String = "") = importInto(folder)
}

@Composable private fun ProjectBackupsDialog(project: File, backups: List<ProjectBackup>, onDismiss: () -> Unit, onCreate: () -> Unit, onRestore: (ProjectBackup) -> Unit) {
    var restoreTarget by remember { mutableStateOf<ProjectBackup?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("RESPALDOS · ${project.name.uppercase()}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se conservan los 5 más recientes, incluidos notebooks y archivos ocultos.", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (backups.isEmpty()) Text("Todavía no hay respaldos.")
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(backups, key = { it.file.path }) { backup ->
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(java.text.SimpleDateFormat("dd-MM-yyyy · HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(backup.createdAtMillis)), fontWeight = FontWeight.Black)
                                Text(humanFileSize(backup.sizeBytes), fontSize = 11.sp)
                            }
                            TextButton(onClick = { restoreTarget = backup }) { Text("RESTAURAR", fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }
        },
        confirmButton = { BrutalButton("＋ CREAR RESPALDO", Green) { onCreate() } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CERRAR", fontWeight = FontWeight.Black) } },
    )
    restoreTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("¿RESTAURAR RESPALDO?", fontWeight = FontWeight.Black) },
            text = { Text("El estado actual se respaldará primero. Luego se reemplazará el contenido del proyecto.") },
            confirmButton = { BrutalButton("RESTAURAR", Green) { restoreTarget = null; onRestore(backup) } },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("CANCELAR") } },
        )
    }
}

@Composable
private fun ProjectWorkflowCard(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 68.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 1)
            }
            Text("→", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable private fun Projects(projects: List<File>, selectedProject: File, files: List<File>, resources: List<File>, current: File, onProject: (File) -> Unit, onNewProject: () -> Unit, onImportProject: () -> Unit, onBackups: () -> Unit, onExportProject: (File) -> Unit, onOpen: (File) -> Unit, onNew: () -> Unit, onImport: () -> Unit, onImportResource: ResourceImporter, onDelete: (File) -> Unit, onDeleteResource: (File) -> Unit, onTrashChanged: () -> Unit, onRename: (File, String) -> Unit, onDuplicate: (File) -> Unit, onShare: (File) -> Unit, onAutomations: () -> Unit, onNotebook: () -> Unit, onPreferences: () -> Unit, onPreviewResource: (File) -> Unit, onResourcePathsChanged: (Map<String, String>) -> Unit, onTemplate: (ScriptTemplate) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var packages by remember { mutableStateOf(false) }
    var trash by remember { mutableStateOf(false) }
    var projectActions by remember { mutableStateOf(false) }
    var templates by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var resourceRenameTarget by remember { mutableStateOf<File?>(null) }
    var resourceActionsTarget by remember { mutableStateOf<File?>(null) }
    var scriptActionsTarget by remember { mutableStateOf<File?>(null) }
    var contentSection by rememberSaveable(selectedProject.path) { mutableStateOf("SCRIPTS") }
    var selectedResources by remember(selectedProject) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedResourceFolder by remember(selectedProject) { mutableStateOf("") }
    val folders = resourceFolders(selectedProject)
    val visibleResources = resources.filter { file ->
        requireNotNull(file.parentFile).relativeTo(selectedProject).invariantSeparatorsPath.takeUnless { it == "." }.orEmpty() == selectedResourceFolder
    }
    var favorites by remember {
        mutableStateOf(context.getSharedPreferences("pixelpy", 0).getStringSet("favorite_templates", emptySet()).orEmpty())
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("PROYECTOS", fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text("Código, datos y herramientas del proyecto.", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                projects.forEach { project ->
                    Surface(onClick = { onProject(project) }, modifier = Modifier.testTag("project-${project.name}"), color = if (project.canonicalFile == selectedProject.canonicalFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (project.canonicalFile == selectedProject.canonicalFile) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(6.dp)) {
                        Text("▣ ${project.name}", Modifier.padding(10.dp, 7.dp), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                Surface(onClick = onNewProject, color = referenceColor(Yellow), contentColor = referenceContentColor(Yellow), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, referenceColor(Outline))) { Text("＋ Nuevo proyecto", Modifier.padding(10.dp, 7.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selectedProject.name, Modifier.testTag("current-project-name"), fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
                    Text("${files.size} scripts · ${resources.size} recursos", fontSize = 11.sp)
                }
                Box {
                    IconButton(onClick = { projectActions = true }) { Icon(Icons.Outlined.MoreVert, "Acciones del proyecto") }
                    DropdownMenu(expanded = projectActions, onDismissRequest = { projectActions = false }) {
                        DropdownMenuItem(text = { Text("Plantillas y favoritos") }, onClick = { projectActions = false; templates = true }, leadingIcon = { Icon(Icons.Outlined.Star, null) })
                        DropdownMenuItem(text = { Text("Apariencia y accesibilidad") }, onClick = { projectActions = false; onPreferences() }, leadingIcon = { Icon(Icons.Outlined.Settings, null) })
                        DropdownMenuItem(text = { Text("Importar archivo .py") }, onClick = { projectActions = false; onImport() }, leadingIcon = { Icon(Icons.Outlined.FileOpen, null) })
                        DropdownMenuItem(text = { Text("Importar proyecto ZIP") }, onClick = { projectActions = false; onImportProject() }, leadingIcon = { Icon(Icons.Outlined.FolderZip, null) })
                        DropdownMenuItem(text = { Text("Exportar proyecto ZIP") }, onClick = { projectActions = false; onExportProject(selectedProject) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) })
                        DropdownMenuItem(text = { Text("Respaldos del proyecto") }, onClick = { projectActions = false; onBackups() }, leadingIcon = { Icon(Icons.Outlined.History, null) })
                        DropdownMenuItem(text = { Text("Librerías incluidas") }, onClick = { projectActions = false; packages = true }, leadingIcon = { Icon(Icons.Outlined.Extension, null) })
                        DropdownMenuItem(text = { Text("Papelera") }, onClick = { projectActions = false; trash = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                    }
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 420.dp
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectWorkflowCard("▦", "ANALIZAR · NOTEBOOK", "Celdas, datos y gráficos", Modifier.fillMaxWidth().testTag("open-notebook"), onNotebook)
                        ProjectWorkflowCard("◷", "AUTOMATIZAR", "Repetir scripts con horario", Modifier.fillMaxWidth().testTag("open-automations"), onAutomations)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectWorkflowCard("▦", "ANALIZAR · NOTEBOOK", "Celdas, datos y gráficos", Modifier.weight(1f).testTag("open-notebook"), onNotebook)
                        ProjectWorkflowCard("◷", "AUTOMATIZAR", "Repetir scripts con horario", Modifier.weight(1f).testTag("open-automations"), onAutomations)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = contentSection == "SCRIPTS", onClick = { contentSection = "SCRIPTS" }, label = { Text("Scripts · ${files.size}") })
                FilterChip(selected = contentSection == "RECURSOS", onClick = { contentSection = "RECURSOS" }, label = { Text("Recursos · ${resources.size}") })
                Spacer(Modifier.weight(1f))
                if (contentSection == "SCRIPTS") IconButton(onClick = onNew) { Icon(Icons.Outlined.Add, "Nuevo script") }
            }
        }
        if (contentSection == "SCRIPTS") items(files, key = { it.path }) { file ->
            Surface(onClick = { onOpen(file) }, modifier = Modifier.fillMaxWidth().testTag("project-open-${file.name}"), color = if (file == current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Code, null, tint = referenceColor(Yellow)) }
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(file.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1); Text("${formatBytes(file.length())}${if (file == current) " · abierto" else " · guardado local"}", fontSize = 11.sp) }
                    Box {
                        IconButton(onClick = { scriptActionsTarget = file }) { Icon(Icons.Outlined.MoreVert, "Más acciones") }
                        DropdownMenu(expanded = scriptActionsTarget == file, onDismissRequest = { scriptActionsTarget = null }) {
                            DropdownMenuItem(text = { Text("Compartir") }, onClick = { scriptActionsTarget = null; onShare(file) }, leadingIcon = { Icon(Icons.Outlined.Share, null) })
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { scriptActionsTarget = null; renameTarget = file }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { scriptActionsTarget = null; onDuplicate(file) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                            DropdownMenuItem(text = { Text("Mover a papelera") }, enabled = files.size > 1, onClick = { scriptActionsTarget = null; onDelete(file) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                        }
                    }
                }
            }
        }
        if (contentSection == "RECURSOS") item {
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RECURSOS", fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Carpetas, selección múltiple y vista previa", fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrutalButton(if (selectedResources.isEmpty()) "＋ CARPETA" else "MOVER ${selectedResources.size}", Yellow, Modifier.weight(1f)) { folderDialog = true }
                    BrutalButton("＋ AÑADIR", Blue, Modifier.weight(1f)) { onImportResource(selectedResourceFolder) }
                }
            }
        }
        if (contentSection == "RECURSOS") item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (selectedResourceFolder.isBlank()) "Destino: raíz del proyecto" else "Destino: $selectedResourceFolder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedResourceFolder.isBlank(), onClick = { selectedResourceFolder = "" }, label = { Text("RAÍZ") })
                    folders.forEach { folder ->
                        val path = resourceRelativePath(selectedProject, folder)
                        FilterChip(selected = selectedResourceFolder == path, onClick = { selectedResourceFolder = path }, label = { Text("▣ $path") })
                    }
                }
            }
        }
        if (contentSection == "RECURSOS" && visibleResources.isEmpty()) item {
            BrutalCard(MaterialTheme.colorScheme.surface) {
                Text("ESTA CARPETA ESTÁ LISTA PARA TUS DATOS", fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text("Añade un CSV, JSON, imagen o documento para usarlo en tus scripts y notebooks.", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                BrutalButton("IMPORTAR RECURSO", Blue, Modifier.fillMaxWidth()) { onImportResource(selectedResourceFolder) }
            }
        }
        if (contentSection == "RECURSOS") items(visibleResources, key = { it.path }) { file ->
            BrutalCard(MaterialTheme.colorScheme.surface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = file.path in selectedResources, onCheckedChange = { checked -> selectedResources = if (checked) selectedResources + file.path else selectedResources - file.path })
                    Box(Modifier.size(44.dp).background(referenceColor(Green)).border(1.dp, referenceColor(Outline)), contentAlignment = Alignment.Center) { Text(file.extension.uppercase().take(4).ifBlank { "FILE" }, color = referenceContentColor(Green), fontWeight = FontWeight.Black, fontSize = 10.sp) }
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(file.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Text(formatBytes(file.length()) + " · ${file.relativeTo(selectedProject).invariantSeparatorsPath}", fontSize = 11.sp) }
                    IconButton(onClick = { onPreviewResource(file) }) { Icon(Icons.Outlined.Visibility, "Vista previa") }
                    Box {
                        IconButton(onClick = { resourceActionsTarget = file }) { Icon(Icons.Outlined.MoreVert, "Más acciones") }
                        DropdownMenu(expanded = resourceActionsTarget == file, onDismissRequest = { resourceActionsTarget = null }) {
                            DropdownMenuItem(text = { Text("Copiar ruta relativa") }, onClick = {
                                clipboard.setText(AnnotatedString(resourceRelativePath(selectedProject, file)))
                                resourceActionsTarget = null
                                android.widget.Toast.makeText(context, "Ruta relativa copiada", android.widget.Toast.LENGTH_SHORT).show()
                            }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                            DropdownMenuItem(text = { Text("Compartir") }, onClick = { resourceActionsTarget = null; onShare(file) }, leadingIcon = { Icon(Icons.Outlined.Share, null) })
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { resourceActionsTarget = null; resourceRenameTarget = file }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                            DropdownMenuItem(text = { Text("Mover a papelera") }, onClick = { resourceActionsTarget = null; onDeleteResource(file) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                        }
                    }
                }
            }
        }
    }
    renameTarget?.let { file -> RenameDialog(file.nameWithoutExtension, onDismiss = { renameTarget = null }) { onRename(file, it); renameTarget = null } }
    resourceRenameTarget?.let { file ->
        RenameDialog(file.name, onDismiss = { resourceRenameTarget = null }) { raw ->
            runCatching { renameResource(selectedProject, file, raw) }
                .onSuccess { change ->
                    onResourcePathsChanged(mapOf(change))
                    resourceRenameTarget = null
                }
                .onFailure { android.widget.Toast.makeText(context, it.message ?: "No se pudo renombrar", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    if (packages) PackagesDialog { packages = false }
    if (trash) TrashDialog(selectedProject, onDismiss = { trash = false }) { onTrashChanged() }
    if (templates) TemplateDialog(favorites, onFavorite = { id ->
        favorites = if (id in favorites) favorites - id else favorites + id
        context.getSharedPreferences("pixelpy", 0).edit().putStringSet("favorite_templates", favorites).apply()
    }, onDismiss = { templates = false }) { template -> templates = false; onTemplate(template) }
    if (folderDialog) ResourceFolderDialog(selectedProject, selectedResources.map(::File), onDismiss = { folderDialog = false }) { changes ->
        selectedResources = emptySet()
        folderDialog = false
        if (changes.isNotEmpty()) onResourcePathsChanged(changes)
        onTrashChanged()
    }
}

@Composable private fun Editor(files: List<File>, current: File, code: TextFieldValue, saveStatus: SaveStatus, onOpen: (File) -> Unit, onDebug: () -> Unit, onTestAutomation: () -> Unit, onCode: (TextFieldValue) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var undo by remember(current.path) { mutableStateOf<List<TextFieldValue>>(emptyList()) }
    var redo by remember(current.path) { mutableStateOf<List<TextFieldValue>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var fontSize by rememberSaveable { mutableFloatStateOf(context.getSharedPreferences("pixelpy", 0).getFloat("font_size", 15f)) }
    var showOutline by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    var moreTools by remember { mutableStateOf(false) }
    val editorVerticalScroll = rememberScrollState()
    val editorHorizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(fontSize) { context.getSharedPreferences("pixelpy", 0).edit().putFloat("font_size", fontSize).apply() }
    LaunchedEffect(code.selection.start, code.text, fontSize) {
        val lineIndex = code.text.take(code.selection.min).count { it == '\n' }
        val lineHeightPx = with(density) { (fontSize + 7).sp.toPx() }
        val lineTop = (lineIndex * lineHeightPx).toInt()
        val lineBottom = lineTop + lineHeightPx.toInt()
        val visibleBottom = editorVerticalScroll.value + editorVerticalScroll.viewportSize
        when {
            lineTop < editorVerticalScroll.value -> editorVerticalScroll.scrollTo(lineTop.coerceAtLeast(0))
            lineBottom > visibleBottom -> editorVerticalScroll.scrollTo(
                (lineBottom - editorVerticalScroll.viewportSize).coerceAtMost(editorVerticalScroll.maxValue).coerceAtLeast(0),
            )
        }
    }
    val projectCompletionFiles = remember(current.parentFile?.path, files) { files.map { it.name } }
    val completionPrefix = code.text.substring(0, code.selection.min).takeLastWhile { it.isLetterOrDigit() || it == '_' }
    val completions = if (completionPrefix.length >= 2) completionItems(
        code.text,
        projectCompletionFiles,
    ).filter { it.startsWith(completionPrefix, ignoreCase = true) && !it.equals(completionPrefix, true) }.take(5) else emptyList()
    fun change(next: TextFieldValue) { if (next.text != code.text) { undo = (undo + code).takeLast(80); redo = emptyList() }; onCode(next) }
    fun insert(value: String) {
        val start = code.selection.min; val end = code.selection.max
        change(TextFieldValue(code.text.replaceRange(start, end, value), TextRange(start + value.length)))
    }
    Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
        if (!keyboardVisible) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            files.forEach { file -> Surface(onClick = { onOpen(file) }, modifier = Modifier.testTag("editor-file-${file.name}"), color = if (file == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (file == current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(6.dp)) { Text((if (file == current) "● " else "") + file.name, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp) } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!keyboardVisible) Text("EDITOR", fontWeight = FontWeight.Black, fontSize = 17.sp)
                else Text(current.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                val statusText = when (saveStatus) {
                    SaveStatus.Editing -> "● EDITANDO"
                    SaveStatus.Saving -> "● GUARDANDO"
                    SaveStatus.Saved -> "● GUARDADO"
                    SaveStatus.Error -> "● ERROR AL GUARDAR"
                }
                Text(statusText, Modifier.testTag("save-status"), color = if (saveStatus == SaveStatus.Error) Color(0xFFC62828) else Color(0xFF16853B), fontWeight = FontWeight.Black, fontSize = 9.sp)
            }
            IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, "Buscar") }
            IconButton(onClick = { if (undo.isNotEmpty()) { redo = redo + code; val previous = undo.last(); undo = undo.dropLast(1); onCode(previous) } }, enabled = undo.isNotEmpty()) { Icon(Icons.Outlined.Undo, "Deshacer") }
            IconButton(onClick = { if (redo.isNotEmpty()) { undo = undo + code; val next = redo.last(); redo = redo.dropLast(1); onCode(next) } }, enabled = redo.isNotEmpty()) { Icon(Icons.Outlined.Redo, "Rehacer") }
            Box {
                IconButton(onClick = { moreTools = true }) { Icon(Icons.Outlined.MoreVert, "Más herramientas") }
                DropdownMenu(moreTools, { moreTools = false }) {
                    DropdownMenuItem({ Text("Depurar ejecución") }, { moreTools = false; onDebug() }, leadingIcon = { Icon(Icons.Outlined.BugReport, null) })
                    DropdownMenuItem({ Text("Deshacer") }, { moreTools = false; if (undo.isNotEmpty()) { redo = redo + code; val previous = undo.last(); undo = undo.dropLast(1); onCode(previous) } }, enabled = undo.isNotEmpty(), leadingIcon = { Icon(Icons.Outlined.Undo, null) })
                    DropdownMenuItem({ Text("Rehacer") }, { moreTools = false; if (redo.isNotEmpty()) { undo = undo + code; val next = redo.last(); redo = redo.dropLast(1); onCode(next) } }, enabled = redo.isNotEmpty(), leadingIcon = { Icon(Icons.Outlined.Redo, null) })
                    DropdownMenuItem({ Text("Probar como automatización") }, { moreTools = false; onTestAutomation() }, leadingIcon = { Icon(Icons.Outlined.Schedule, null) })
                    DropdownMenuItem({ Text("Estructura del código") }, { moreTools = false; showOutline = true }, leadingIcon = { Icon(Icons.Outlined.AccountTree, null) })
                    DropdownMenuItem({ Text("Versiones anteriores") }, { moreTools = false; showVersions = true }, leadingIcon = { Icon(Icons.Outlined.Restore, null) })
                    DropdownMenuItem({ Text("Reducir texto") }, { moreTools = false; fontSize = (fontSize - 1).coerceAtLeast(11f) }, leadingIcon = { Text("A−", fontWeight = FontWeight.Black) })
                    DropdownMenuItem({ Text("Aumentar texto") }, { moreTools = false; fontSize = (fontSize + 1).coerceAtMost(24f) }, leadingIcon = { Text("A+", fontWeight = FontWeight.Black) })
                }
            }
        }
        if (searching) {
            Column(Modifier.fillMaxWidth().background(referenceColor(Yellow)).border(1.dp, referenceColor(Outline)).padding(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Buscar") }, singleLine = true)
                    BrutalButton("SIG.", Blue, enabled = query.isNotEmpty()) { val start = if (code.selection.max < code.text.length) code.selection.max else 0; val found = code.text.indexOf(query, start, ignoreCase = true).let { if (it < 0) code.text.indexOf(query, 0, ignoreCase = true) else it }; if (found >= 0) onCode(code.copy(selection = TextRange(found, found + query.length))) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(replacement, { replacement = it }, Modifier.weight(1f), label = { Text("Reemplazar por") }, singleLine = true)
                    BrutalButton("UNO", Green, enabled = query.isNotEmpty()) { if (code.selection.min != code.selection.max && code.text.substring(code.selection.min, code.selection.max).equals(query, true)) { val start = code.selection.min; change(TextFieldValue(code.text.replaceRange(code.selection.min, code.selection.max, replacement), TextRange(start + replacement.length))) } }
                    BrutalButton("TODO", Pink, enabled = query.isNotEmpty()) { change(TextFieldValue(code.text.replace(query, replacement, ignoreCase = true))) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(end = 5.dp, bottom = 5.dp)) {

            Row(Modifier.fillMaxSize().background(Color(0xFF20232A), RoundedCornerShape(12.dp)).verticalScroll(editorVerticalScroll).padding(10.dp)) {
                Text(lineNumberText(code.text), Modifier.width(46.dp), color = Color(0xFF7D8590), fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Spacer(Modifier.width(10.dp)); Box(Modifier.width(2.dp).fillMaxHeight().background(Color(0xFF444C56))); Spacer(Modifier.width(10.dp))
                BasicTextField(value = code, onValueChange = { raw -> change(autoIndent(code, raw)) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii), modifier = Modifier.testTag("editor-input").fillMaxSize().horizontalScroll(editorHorizontalScroll), textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = PythonHighlight)
            }
        }
        Text("Ln ${code.text.take(code.selection.min).count { it == '\n' } + 1} · Col ${code.text.take(code.selection.min).substringAfterLast('\n').length + 1} · Python · UTF-8", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), modifier = Modifier.padding(top = 4.dp))
        if (completions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                completions.forEach { suggestion -> Surface(onClick = { val end = code.selection.min; val start = end - completionPrefix.length; change(TextFieldValue(code.text.replaceRange(start, end, suggestion), TextRange(start + suggestion.length))) }, color = referenceColor(Pink), contentColor = referenceContentColor(Pink), border = BorderStroke(1.dp, referenceColor(Outline)), shape = RoundedCornerShape(5.dp)) { Text(suggestion, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) } }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("⇥" to "indent", "⇤" to "outdent", "# /" to "comment").forEach { (label, action) ->
                Surface(onClick = { change(editSelectedLines(code, action)) }, color = referenceColor(Green), contentColor = referenceContentColor(Green), border = BorderStroke(1.dp, referenceColor(Outline)), shape = RoundedCornerShape(5.dp)) { Text(label, Modifier.padding(horizontal = 14.dp, vertical = 12.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            }
            listOf("TAB" to "    ", "(" to "()", "[" to "[]", "{" to "{}", ":" to ":", "'" to "''", "\"" to "\"\"", "=" to " = ", "#" to "# ").forEach { (label, value) ->
                Surface(onClick = { insertPair(code, value, ::change) }, color = referenceColor(Blue), contentColor = referenceContentColor(Blue), border = BorderStroke(1.dp, referenceColor(Outline)), shape = RoundedCornerShape(5.dp)) { Text(label, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black) }
            }
        }
    }
    if (showOutline) CodeOutlineDialog(code.text, onDismiss = { showOutline = false }) { line -> val position = code.text.lineSequence().take(line - 1).sumOf { it.length + 1 }; onCode(code.copy(selection = TextRange(position.coerceAtMost(code.text.length)))); showOutline = false }
    if (showVersions) VersionsDialog(current, onDismiss = { showVersions = false }) { version -> onCode(TextFieldValue(version.readText())); showVersions = false; android.widget.Toast.makeText(context, "Versión restaurada y guardada", android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable private fun ReplScreen(project: File, entries: List<ReplEntry>, runtimeBusy: Boolean, onEntries: (List<ReplEntry>) -> Unit) {
    var command by rememberSaveable(project.path) { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun evaluate() {
        val source = command.trim(); if (source.isEmpty() || running || runtimeBusy) return
        running = true; command = ""
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    PythonRuntimeCoordinator.runExclusive {
                        val value = Python.getInstance().getModule("runner").callAttr("repl_eval", source, project.absolutePath)
                        value.callAttr("get", "ok").toBoolean() to value.callAttr("get", "output").toString()
                    }
                }
                onEntries((entries + ReplEntry(source, result.second.ifBlank { "✓" }, result.first)).takeLast(100))
            } catch (error: Exception) {
                onEntries((entries + ReplEntry(source, "Runtime: ${error.message}", false)).takeLast(100))
            } finally {
                running = false
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(14.dp)) {
        Text("PYTHON REPL", fontWeight = FontWeight.Black, fontSize = 27.sp)
        Text(if (runtimeBusy) "Runtime Python ocupado…" else "Sesión activa · ${project.name}", color = if (runtimeBusy) referenceColor(Pink) else MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Ink).border(3.dp, Ink).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entries.isEmpty()) item { Text("Prueba expresiones sin crear un archivo.\n\n>>> 2 ** 10\n>>> datos = [3, 1, 2]\n>>> sorted(datos)", color = Color(0xFF9DA7B3), fontFamily = FontFamily.Monospace) }
            items(entries) { entry -> Column { Text(">>> ${entry.command}", color = referenceColor(Yellow), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Text(entry.output, color = if (entry.ok) Color.White else referenceColor(Pink), fontFamily = FontFamily.Monospace) } }
        }
        Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(command, { command = it }, Modifier.weight(1f), label = { Text(">>> comando") }, enabled = !runtimeBusy, textStyle = TextStyle(fontFamily = FontFamily.Monospace), maxLines = 4)
            BrutalButton(if (runtimeBusy) "…" else "EJECUTAR", Green, enabled = !runtimeBusy, onClick = ::evaluate)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { onEntries(emptyList()) }, enabled = entries.isNotEmpty()) { Text("LIMPIAR", color = Ink, fontWeight = FontWeight.Black) } }
    }
}

@Composable private fun Console(fileName: String, code: String, output: String, generated: List<File>, sessions: List<ExecutionSession>, success: Boolean?, running: Boolean, errorLine: Int?, onBack: () -> Unit, onGoToLine: (Int) -> Unit, onRun: () -> Unit, onRunWithParameters: (Map<String, String>) -> Unit, onView: (File) -> Unit, onSave: (File) -> Unit, onShare: (File) -> Unit, onOpenSessionArtifact: (ExecutionSession, String) -> Unit, onPinSession: (ExecutionSession) -> Unit, onDeleteSession: (ExecutionSession) -> Unit, onExportSession: (ExecutionSession) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val report = "PY.STUDIO LOG\n\nCÓDIGO:\n$code\n\nSALIDA:\n$output"
    val explanation = explainError(output)
    var showHistory by remember { mutableStateOf(false) }
    var consolePage by remember { mutableStateOf("SALIDA") }
    var consoleSearch by remember { mutableStateOf("") }
    var parameterRun by remember { mutableStateOf(false) }
    var consoleActions by remember { mutableStateOf(false) }
    var showGenerated by remember { mutableStateOf(generated.isNotEmpty()) }
    val debugMarker = "DEPURACIÓN · LÍNEAS Y VARIABLES"
    val standardOutput = output.substringBefore(debugMarker).trim()
    val debugOutput = output.substringAfter(debugMarker, "").trim()
    val sectionOutput = when (consolePage) { "VARIABLES" -> debugOutput.ifBlank { "Ejecuta con DEBUG para inspeccionar líneas y variables." }; "ERROR" -> if (success == false) output else "No hay errores en la última ejecución."; else -> standardOutput.ifBlank { when { running -> "Ejecutando… la salida aparecerá aquí."; success == null -> "Ejecuta un script desde el editor para ver sus resultados."; else -> "✓ Programa terminado sin salida." } } }
    val visibleOutput = if (consoleSearch.isBlank()) sectionOutput else sectionOutput.lineSequence().filter { it.contains(consoleSearch, true) }.joinToString("\n").ifBlank { "Sin coincidencias." }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("CONSOLA", fontWeight = FontWeight.Black, fontSize = 24.sp); Spacer(Modifier.weight(1f)); IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, "Historial") }; Box { IconButton(onClick = { consoleActions = true }) { Icon(Icons.Outlined.MoreVert, "Más acciones") }; DropdownMenu(consoleActions, { consoleActions = false }) { DropdownMenuItem({ Text("Copiar sección") }, { consoleActions = false; clipboard.setText(AnnotatedString(sectionOutput)); android.widget.Toast.makeText(context, "Sección copiada", android.widget.Toast.LENGTH_SHORT).show() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }); DropdownMenuItem({ Text("Compartir log") }, { consoleActions = false; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }, "Compartir log")) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }); DropdownMenuItem({ Text("Repetir con parámetros") }, { consoleActions = false; parameterRun = true }, enabled = !running, leadingIcon = { Icon(Icons.Outlined.Tune, null) }) } }; val status = when { running -> Yellow; success == true -> Success; success == false -> Pink; else -> Blue }; Surface(color = referenceColor(status), contentColor = referenceContentColor(status), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, referenceColor(Outline))) { Text(if (running) "EJECUTANDO" else if (success == false) "ERROR" else "LISTO", Modifier.padding(8.dp, 4.dp), fontWeight = FontWeight.Black, fontSize = 10.sp) } }
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("SALIDA" to Green, "VARIABLES" to Blue, "ERROR" to Pink).forEach { (label, color) -> val active = consolePage == label; Surface(onClick = { consolePage = label }, color = if (active) referenceColor(color) else MaterialTheme.colorScheme.surface, contentColor = if (active) referenceContentColor(color) else MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(5.dp)) { Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Black, fontSize = 10.sp) } } }
        OutlinedTextField(consoleSearch, { consoleSearch = it }, Modifier.fillMaxWidth(), label = { Text("Buscar en esta sección") }, singleLine = true)
        Spacer(Modifier.height(8.dp)); BrutalCard(Ink, Modifier.weight(1f)) { Text("$ python $fileName", color = referenceColor(Green), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(visibleOutput, color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("console-output").verticalScroll(rememberScrollState())) }
        Text("PANEL · ${generated.size} archivo(s) · ${formatBytes(generated.sumOf(File::length))} modificados · memoria app aprox. ${formatBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (explanation != null) { Spacer(Modifier.height(10.dp)); BrutalCard(Yellow) { Text("QUÉ SIGNIFICA", fontWeight = FontWeight.Black, fontSize = 11.sp); Text(explanation, fontWeight = FontWeight.Medium) } }
        if (errorLine != null) { Spacer(Modifier.height(10.dp)); BrutalButton("IR A LÍNEA $errorLine", Pink, Modifier.fillMaxWidth()) { onGoToLine(errorLine) } }
        if (generated.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(onClick = { showGenerated = !showGenerated }, color = referenceColor(Blue), contentColor = referenceContentColor(Blue), border = BorderStroke(1.dp, referenceColor(Outline)), shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth()) { Text("${if (showGenerated) "▾" else "▸"} ARCHIVOS GENERADOS · ${generated.size}", Modifier.padding(10.dp, 7.dp), fontWeight = FontWeight.Black, fontSize = 11.sp) }
            if (showGenerated) LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(generated, key = { it.path }) { file ->
                    BrutalCard(Blue, Modifier.width(292.dp)) {
                        Text(file.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(formatBytes(file.length()), fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        BrutalButton("VER RESULTADO", Blue, Modifier.fillMaxWidth()) { onView(file) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { onSave(file) }) { Text("GUARDAR", color = Ink, fontWeight = FontWeight.Black) }; TextButton(onClick = { onShare(file) }) { Text("COMPARTIR", color = Ink, fontWeight = FontWeight.Black) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { BrutalButton("← EDITAR", Blue, Modifier.weight(1f), onClick = onBack); BrutalButton("↻ REPETIR", Yellow, Modifier.weight(1f), enabled = !running, onClick = onRun) }
    }
    if (showHistory) HistoryDialog(
        sessions = sessions,
        onOpenArtifact = onOpenSessionArtifact,
        onPin = onPinSession,
        onDelete = onDeleteSession,
        onExport = onExportSession,
        onDismiss = { showHistory = false },
    )
    if (parameterRun) QuickParametersDialog(onDismiss = { parameterRun = false }) { values ->
        parameterRun = false
        onRunWithParameters(values)
    }
}

@Composable private fun BrutalCard(color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = referenceColor(color), contentColor = referenceContentColor(color), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable private fun BrutalButton(label: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = referenceColor(color), contentColor = referenceContentColor(color), disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text(label, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp) }
}

@Composable private fun NewFileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(5.dp), title = { Text("NUEVO ARCHIVO", fontWeight = FontWeight.Black) }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre sin .py") }, singleLine = true) }, confirmButton = { BrutalButton("CREAR", Green, enabled = name.isNotBlank()) { onCreate(name) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } })
}

@Composable private fun RenameDialog(initial: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(5.dp), title = { Text("RENOMBRAR", fontWeight = FontWeight.Black) }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre del archivo") }, singleLine = true) }, confirmButton = { BrutalButton("GUARDAR", Green, enabled = name.isNotBlank()) { onRename(name) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } })
}

@Composable private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(5.dp), title = { Text("NUEVO PROYECTO", fontWeight = FontWeight.Black) }, text = { Column { Text("Creará una carpeta independiente para sus módulos y archivos."); Spacer(Modifier.height(10.dp)); OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true) } }, confirmButton = { BrutalButton("CREAR PROYECTO", Green, enabled = name.isNotBlank()) { onCreate(name) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } })
}

private data class LibraryProfile(val name: String, val description: String, val modules: List<String>)
private val LIBRARY_PROFILES = listOf(
    LibraryProfile("CORE", "CSV, JSON, fechas y archivos", listOf("csv", "json", "datetime", "pathlib")),
    LibraryProfile("WEB", "requests + BeautifulSoup", listOf("requests", "bs4")),
    LibraryProfile("EXCEL", "openpyxl + XML seguro", listOf("openpyxl", "defusedxml")),
    LibraryProfile("DATA LAB", "Resultados y gráficos SVG locales", listOf("pixelpy_chart")),
)

@Composable private fun PackagesDialog(onDismiss: () -> Unit) {
    var validation by remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }
    LaunchedEffect(Unit) {
        validation = LIBRARY_PROFILES.associate { it.name to null }
        validation = withContext(Dispatchers.Default) {
            PythonRuntimeCoordinator.runExclusive {
                val importer = Python.getInstance().getModule("builtins").get("__import__")
                LIBRARY_PROFILES.associate { profile ->
                    profile.name to runCatching { profile.modules.forEach { importer?.call(it) } }.isSuccess
                }
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), title = { Text("PERFILES DE LIBRERÍAS", fontWeight = FontWeight.Black) }, text = {
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(LIBRARY_PROFILES, key = { it.name }) { profile ->
                val state = validation[profile.name]
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val stateColor = if (state == true) Success else if (state == false) Pink else Yellow
                    Box(Modifier.size(42.dp).background(referenceColor(stateColor)).border(1.dp, referenceColor(Outline)), contentAlignment = Alignment.Center) {
                        Text(if (state == true) "✓" else if (state == false) "!" else "…", fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.Black)
                        Text(profile.description, fontSize = 12.sp)
                        Text(profile.modules.joinToString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
            item { Text("Perfiles incluidos y probados dentro de la app. py.Studio no instala paquetes libres en tiempo de ejecución.", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }, confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) })
}

@Composable private fun HistoryDialog(
    sessions: List<ExecutionSession>,
    onOpenArtifact: (ExecutionSession, String) -> Unit,
    onPin: (ExecutionSession) -> Unit,
    onDelete: (ExecutionSession) -> Unit,
    onExport: (ExecutionSession) -> Unit,
    onDismiss: () -> Unit,
) {
    var comparison by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ExecutionSession?>(null) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), title = { Text("HISTORIAL", fontWeight = FontWeight.Black) }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sessions.isEmpty()) item { Text("Todavía no hay ejecuciones guardadas.") }
            comparison?.let { message ->
                item {
                    Surface(color = referenceColor(Blue), contentColor = referenceContentColor(Blue), border = BorderStroke(1.dp, referenceColor(Outline))) {
                        Text(message, Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            items(sessions, key = { it.id }) { session ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).border(1.dp, referenceColor(if (session.success) Success else Pink)).clickable { detail = session }.padding(10.dp)) {
                    Row {
                        Text(if (session.success) "✓ " else "✕ ", fontWeight = FontWeight.Black)
                        Text(session.scriptName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (session.pinned) Text("★ ", fontWeight = FontWeight.Black)
                        Text(java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.startedAtMillis)), fontSize = 10.sp)
                    }
                    Text(session.output.lineSequence().firstOrNull().orEmpty().ifBlank { "Sin salida" }, maxLines = 2, fontSize = 12.sp)
                    Text("${session.durationMillis} ms · ${session.artifacts.size} resultado(s)", fontSize = 10.sp)
                    session.artifacts.forEach { artifact ->
                        TextButton(onClick = { onOpenArtifact(session, artifact) }) {
                            Text("VER ${File(artifact).name}", fontWeight = FontWeight.Black)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onPin(session) }) { Text(if (session.pinned) "SOLTAR" else "FIJAR") }
                        TextButton(onClick = { onExport(session) }) { Text("ZIP") }
                        TextButton(onClick = {
                            val previous = sessions.dropWhile { it.id != session.id }.drop(1)
                                .firstOrNull { it.projectPath == session.projectPath && it.scriptName == session.scriptName }
                            comparison = if (previous == null) {
                                "No existe una ejecución anterior comparable."
                            } else {
                                val diff = compareTextLines(previous.output, session.output)
                                "Comparación de salida: +${diff.added} · −${diff.removed} · ${diff.unchanged} iguales"
                            }
                        }) { Text("COMPARAR") }
                        TextButton(onClick = { onDelete(session) }) { Text("BORRAR", color = Color(0xFF9A001F)) }
                    }
                }
            }
        }
    }, confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) })
    detail?.let { session ->
        val previous = sessions.dropWhile { it.id != session.id }.drop(1)
            .firstOrNull { it.projectPath == session.projectPath && it.scriptName == session.scriptName }
        SessionDetailDialog(session, previous) { detail = null }
    }
}

@Composable private fun TemplateDialog(favorites: Set<String>, onFavorite: (String) -> Unit, onDismiss: () -> Unit, onUse: (ScriptTemplate) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        title = { Text("PLANTILLAS", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PIXELPY_TEMPLATES.sortedByDescending { it.id in favorites }, key = { it.id }) { template ->
                    Column(Modifier.fillMaxWidth().background(if (template.id in favorites) referenceColor(Yellow) else MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(template.name, fontWeight = FontWeight.Black); Text(template.description, fontSize = 11.sp) }
                            IconButton(onClick = { onFavorite(template.id) }) { Text(if (template.id in favorites) "★" else "☆", fontSize = 22.sp) }
                        }
                        BrutalButton("USAR PLANTILLA", Green, Modifier.fillMaxWidth()) { onUse(template) }
                    }
                }
            }
        },
        confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) },
    )
}

@Composable private fun ResourceFolderDialog(project: File, selected: List<File>, onDismiss: () -> Unit, onDone: (Map<String, String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val folders = remember(project) { resourceFolders(project) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        title = { Text(if (selected.isEmpty()) "NUEVA CARPETA" else "MOVER RECURSOS", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Carpeta") }, singleLine = true)
                folders.forEach { folder -> TextButton(onClick = { name = folder.relativeTo(project).invariantSeparatorsPath }) { Text(folder.relativeTo(project).invariantSeparatorsPath) } }
                if (error.isNotBlank()) Text(error, color = Color(0xFFC62828))
            }
        },
        confirmButton = { BrutalButton(if (selected.isEmpty()) "CREAR" else "MOVER", Green, enabled = name.isNotBlank()) {
            runCatching {
                if (selected.isEmpty()) {
                    createResourceFolder(project, name)
                    emptyMap()
                } else moveResources(project, selected, name)
            }.onSuccess(onDone).onFailure { error = it.message ?: "No se pudo completar" }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

@Composable private fun PreferencesDialog(dark: Boolean, contrast: Boolean, scale: Float, onDismiss: () -> Unit, onSave: (Boolean, Boolean, Float) -> Unit) {
    var darkMode by remember { mutableStateOf(dark) }
    var highContrast by remember { mutableStateOf(contrast) }
    var uiScale by remember { mutableFloatStateOf(scale) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }
    val scaleLabel = when (uiScale) { .9f -> "COMPACTA"; 1.15f -> "AMPLIA"; else -> "NORMAL" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        title = { Text("APARIENCIA", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Tema oscuro", Modifier.weight(1f)); Switch(darkMode, { darkMode = it }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Contraste alto", Modifier.weight(1f)); Switch(highContrast, { highContrast = it }) }
                Text("Densidad del texto", fontWeight = FontWeight.Black)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = { scaleMenuExpanded = !scaleMenuExpanded },
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().testTag("appearance-density-selector"),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(scaleLabel, Modifier.weight(1f), fontWeight = FontWeight.Black)
                            Icon(
                                if (scaleMenuExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (scaleMenuExpanded) "Cerrar densidades" else "Elegir densidad",
                            )
                        }
                    }
                    if (scaleMenuExpanded) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                listOf(.9f to "Compacta", 1f to "Normal", 1.15f to "Amplia").forEach { (value, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("appearance-density-option-${value.toString().replace('.', '-')}")
                                            .clickable { uiScale = value; scaleMenuExpanded = false }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(label, Modifier.weight(1f))
                                        if (uiScale == value) Icon(Icons.Outlined.Check, contentDescription = "Seleccionada")
                                    }
                                }
                            }
                        }
                    }
                }
                Text("El tamaño del código se ajusta por separado desde Más herramientas en el editor.", fontSize = 11.sp)
            }
        },
        confirmButton = { BrutalButton("GUARDAR", Green) { onSave(darkMode, highContrast, uiScale); onDismiss() } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

@Composable private fun QuickParametersDialog(onDismiss: () -> Unit, onRun: (Map<String, String>) -> Unit) {
    var rows by remember { mutableStateOf(listOf("" to "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        title = { Text("REPETIR CON PARÁMETROS", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Se exponen temporalmente con os.getenv().", fontSize = 11.sp)
                rows.forEachIndexed { index, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(pair.first, { value -> rows = rows.toMutableList().also { it[index] = value.uppercase() to pair.second } }, Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true)
                        OutlinedTextField(pair.second, { value -> rows = rows.toMutableList().also { it[index] = pair.first to value } }, Modifier.weight(1f), label = { Text("Valor") }, singleLine = true)
                    }
                }
                TextButton(onClick = { rows = rows + ("" to "") }) { Text("＋ OTRO") }
            }
        },
        confirmButton = { BrutalButton("EJECUTAR", Yellow) {
            val values = rows.filter { it.first.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }.toMap()
            onRun(values)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

@Composable private fun SessionDetailDialog(session: ExecutionSession, previous: ExecutionSession?, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf("SALIDA") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        title = { Text("${if (session.success) "✓" else "✕"} ${session.scriptName}", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("${session.durationMillis} ms · ${session.artifacts.size} archivo(s) · ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(session.startedAtMillis))}", fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("SALIDA", "CÓDIGO", "CAMBIOS", "ARCHIVOS").forEach { option ->
                        FilterChip(selected = page == option, onClick = { page = option }, label = { Text(option) })
                        Spacer(Modifier.width(5.dp))
                    }
                }
                val content = when (page) {
                    "CÓDIGO" -> session.source
                    "ARCHIVOS" -> session.artifacts.joinToString("\n").ifBlank { "No se modificaron archivos." }
                    "CAMBIOS" -> previous?.let { old ->
                        lineDiff(old.output, session.output).joinToString("\n") { line ->
                            "${when (line.kind) { DiffKind.Added -> "+"; DiffKind.Removed -> "−"; DiffKind.Unchanged -> " " }} ${line.text}"
                        }
                    } ?: "No existe una ejecución anterior comparable."
                    else -> session.output
                }
                Text(content, Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).background(Ink).padding(10.dp), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        },
        confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) },
    )
}

@Composable private fun CodeOutlineDialog(source: String, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val symbols = remember(source) { codeSymbols(source) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), title = { Text("ESTRUCTURA", fontWeight = FontWeight.Black) }, text = { LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (symbols.isEmpty()) item { Text("No se encontraron funciones ni clases.") }
        items(symbols) { symbol -> val chip = if (symbol.kind == "class") Pink else Blue; Surface(onClick = { onGo(symbol.line) }, color = referenceColor(chip), contentColor = referenceContentColor(chip), border = BorderStroke(1.dp, referenceColor(Outline)), shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp)) { Text(if (symbol.kind == "class") "C" else "ƒ", fontWeight = FontWeight.Black); Spacer(Modifier.width(10.dp)); Text(symbol.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("L${symbol.line}", fontSize = 11.sp) } } }
    } }, confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) })
}

@Composable private fun TrashDialog(project: File, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val trash = remember(project, refresh) { File(project, ".trash").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), title = { Text("PAPELERA", fontWeight = FontWeight.Black) }, text = { LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (trash.isEmpty()) item { Text("La papelera está vacía.") }
        items(trash, key = { it.path }) { file -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(file.name.substringAfter("__"), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); BrutalButton("RESTAURAR", Green) { restoreTrash(file, project); refresh++; onChanged() } } }
    } }, confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) })
}

@Composable private fun VersionsDialog(file: File, onDismiss: () -> Unit, onRestore: (File) -> Unit) {
    val versions = remember(file) { File(file.parentFile, ".versions/${file.name}").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), title = { Text("VERSIONES", fontWeight = FontWeight.Black) }, text = { LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (versions.isEmpty()) item { Text("Se creará una versión automática antes de cada ejecución.") }
        items(versions, key = { it.path }) { version -> Row(Modifier.fillMaxWidth().background(referenceColor(Blue)).border(1.dp, referenceColor(Outline)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(version.lastModified())), fontWeight = FontWeight.Bold); Text("${version.readLines().size} líneas", fontSize = 11.sp) }; BrutalButton("RESTAURAR", Green) { onRestore(version) } } }
    } }, confirmButton = { BrutalButton("CERRAR", Yellow, onClick = onDismiss) })
}

@Composable private fun InputDialog(prompt: String, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var answer by remember(prompt) { mutableStateOf("") }
    AlertDialog(onDismissRequest = {}, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(5.dp), title = { Text("RESPUESTA DEL SCRIPT", fontWeight = FontWeight.Black) }, text = { Column { Text(prompt, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); OutlinedTextField(answer, { answer = it }, label = { Text("Tu respuesta") }, singleLine = true) } }, confirmButton = { BrutalButton("ENVIAR ↵", Green) { onSubmit(answer) } }, dismissButton = { TextButton(onClick = onCancel) { Text("DETENER SCRIPT") } })
}

private object PythonHighlight : VisualTransformation {
    private val words = setOf("and","as","assert","async","await","break","class","continue","def","del","elif","else","except","False","finally","for","from","global","if","import","in","is","lambda","None","nonlocal","not","or","pass","raise","return","True","try","while","with","yield","print","input","range","len")
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        Regex("#[^\\n]*").findAll(text.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFF8B949E)), it.range.first, it.range.last + 1) }
        Regex("(?:\\\"[^\\\"\\n]*\\\"|'[^'\\n]*')").findAll(text.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFFA5D6FF)), it.range.first, it.range.last + 1) }
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(text.text).filter { it.value in words }.forEach { builder.addStyle(SpanStyle(color = Color(0xFFFF7AB2), fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1) }
        Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(text.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFFFFE34D)), it.range.first, it.range.last + 1) }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun autoIndent(old: TextFieldValue, next: TextFieldValue): TextFieldValue {
    if (next.text.length != old.text.length + 1 || next.selection.start < 1 || next.text[next.selection.start - 1] != '\n') return next
    val previousLine = old.text.substring(0, old.selection.min).substringAfterLast('\n')
    val base = previousLine.takeWhile { it == ' ' || it == '\t' }
    val indent = base + if (previousLine.trimEnd().endsWith(':')) "    " else ""
    if (indent.isEmpty()) return next
    val cursor = next.selection.start
    return TextFieldValue(next.text.substring(0, cursor) + indent + next.text.substring(cursor), TextRange(cursor + indent.length))
}

internal fun lineNumberText(source: String): String =
    (1..(source.count { it == '\n' } + 1)).joinToString("\n")

private fun insertPair(current: TextFieldValue, value: String, onChange: (TextFieldValue) -> Unit) {
    val start = current.selection.min; val end = current.selection.max
    val selected = current.text.substring(start, end)
    val paired = value.length == 2 && value in listOf("()", "[]", "{}", "''", "\"\"")
    val inserted = if (paired && selected.isNotEmpty()) "${value.first()}$selected${value.last()}" else value
    val cursor = if (paired && selected.isEmpty()) start + 1 else start + inserted.length
    onChange(TextFieldValue(current.text.replaceRange(start, end, inserted), TextRange(cursor)))
}

private fun completionItems(source: String, projectFiles: List<String> = emptyList()): List<String> {
    val keywords = listOf("and","as","assert","async","await","break","class","continue","def","elif","else","except","False","finally","for","from","if","import","in","is","lambda","None","not","or","pass","raise","return","True","try","while","with","yield","print","input","range","len","list","dict","set","str","int","float","enumerate","zip","open","super")
    val names = Regex("\\b(?:def|class)\\s+([A-Za-z_]\\w*)|\\b([A-Za-z_]\\w*)\\s*=").findAll(source).flatMap { match -> match.groupValues.drop(1).filter { it.isNotBlank() }.asSequence() }.toList()
    val imports = Regex("""(?:import|from)\s+([A-Za-z_][\w.]*)""").findAll(source).map { it.groupValues[1].substringBefore('.') }.toList()
    return (names + imports + projectFiles.map { "\"$it\"" } + keywords).distinct()
}

private fun codeSymbols(source: String): List<CodeSymbol> = source.lineSequence().mapIndexedNotNull { index, line ->
    Regex("^\\s*(def|class)\\s+([A-Za-z_]\\w*)").find(line)?.let { CodeSymbol(it.groupValues[1], it.groupValues[2], index + 1) }
}.toList()

private fun seed(dir: File): List<File> {
    if (dir.listFiles().isNullOrEmpty()) {
        writeUtf8Atomically(File(dir, "hola_mundo.py"), "# Tu primer programa\nnombre = input(\"¿Cómo te llamas? \" )\nprint(f\"¡Hola, {nombre}!\")\n")
        writeUtf8Atomically(File(dir, "lista_creativa.py"), "ideas = [\"dibujar\", \"caminar\", \"crear una app\"]\n\nfor numero, idea in enumerate(ideas, 1):\n    print(f\"{numero}. {idea}\")\n")
    }
    File(dir, "input_interactivo.py").let { file -> if (!file.exists()) writeUtf8Atomically(file, "nombre = input(\"¿Cómo te llamas? \" )\nedad = input(\"¿Cuántos años tienes? \" )\nprint(f\"Hola {nombre}, tienes {edad} años.\")\n") }
    File(dir, "librerias_demo.py").let { file -> if (!file.exists()) writeUtf8Atomically(file, "import requests\nfrom bs4 import BeautifulSoup\nfrom openpyxl import Workbook\n\nrespuesta = requests.get(\"https://example.com\", timeout=15)\nsoup = BeautifulSoup(respuesta.text, \"html.parser\")\n\nlibro = Workbook()\nhoja = libro.active\nhoja.append([\"Título web\", soup.title.string])\nlibro.save(\"demo_librerias.xlsx\")\n\nprint(\"OK:\", soup.title.string)\nprint(\"Excel generado con openpyxl\")\n") }
    return dir.listFiles { file -> file.extension == "py" }!!.sortedBy { it.name }
}

private fun prepareProjects(root: File): File {
    val looseFiles = root.listFiles { file -> file.isFile }?.toList().orEmpty()
    var principal = File(root, "Proyecto principal")
    if (looseFiles.isNotEmpty() || root.listFiles { file -> file.isDirectory }.isNullOrEmpty()) principal.mkdirs()
    looseFiles.forEach { source ->
        var target = File(principal, source.name); var number = 2
        while (target.exists()) { target = File(principal, source.nameWithoutExtension + "_migrado$number" + if (source.extension.isNotEmpty()) ".${source.extension}" else ""); number++ }
        if (!source.renameTo(target)) runCatching { source.copyTo(target); source.delete() }
    }
    return root.listFiles { file -> file.isDirectory }?.firstOrNull { it.name == "Proyecto principal" }
        ?: root.listFiles { file -> file.isDirectory }?.sortedBy { it.name }?.firstOrNull()
        ?: principal.apply { mkdirs() }
}

private fun backupFile(file: File) {
    if (!file.exists()) return
    val folder = File(file.parentFile, ".versions/${file.name}").apply { mkdirs() }
    val latest = folder.listFiles()?.maxByOrNull { it.lastModified() }
    if (latest != null && runCatching { latest.readText() == file.readText() }.getOrDefault(false)) return
    file.copyTo(File(folder, "${System.currentTimeMillis()}.py"), overwrite = false)
    folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
}

private fun moveToTrash(file: File, project: File = requireNotNull(file.parentFile)): Boolean {
    val folder = File(project, ".trash").apply { mkdirs() }
    return file.renameTo(File(folder, "${System.currentTimeMillis()}__${file.name}"))
}

private fun restoreTrash(file: File, project: File) {
    val original = file.name.substringAfter("__"); var target = File(project, original); var number = 2
    while (target.exists()) { target = File(project, original.substringBeforeLast('.', original) + "_restaurado$number" + original.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }); number++ }
    file.renameTo(target)
}

private fun unzipProject(input: java.io.InputStream, target: File) {
    var total = 0L
    ZipInputStream(input.buffered()).use { zip -> var entry = zip.nextEntry; while (entry != null) {
        val output = File(target, entry.name); if (!output.canonicalPath.startsWith(target.canonicalPath + File.separator)) throw SecurityException("Ruta ZIP inválida")
        if (entry.isDirectory) output.mkdirs() else { output.parentFile?.mkdirs(); output.outputStream().use { stream -> val buffer = ByteArray(8192); var read = zip.read(buffer); while (read > 0) { total += read; if (total > 100L * 1024 * 1024) throw IllegalArgumentException("ZIP demasiado grande"); stream.write(buffer, 0, read); read = zip.read(buffer) } } }
        zip.closeEntry(); entry = zip.nextEntry
    } }
}

private fun mime(file: File) = when (file.extension.lowercase()) {
    "py" -> "text/x-python"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "zip" -> "application/zip"
    "txt", "log" -> "text/plain"
    else -> "application/octet-stream"
}

private fun contentName(context: android.content.Context, uri: android.net.Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) return cursor.getString(0) ?: "" }
    return uri.lastPathSegment?.substringAfterLast('/') ?: ""
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun explainError(output: String): String? = when {
    "SyntaxError" in output -> "Python no pudo entender la estructura del código. Revisa paréntesis, comillas y los dos puntos cerca de la línea indicada."
    "IndentationError" in output || "TabError" in output -> "La sangría no coincide. Alinea el bloque usando cuatro espacios y evita mezclar tabulaciones con espacios."
    "NameError" in output -> "Se usó un nombre que Python todavía no conoce. Comprueba su escritura y que la variable se defina antes de utilizarla."
    "TypeError" in output -> "La operación recibió un tipo de dato incompatible. Revisa si estás mezclando texto, números, listas u otros valores."
    "IndexError" in output -> "Se intentó acceder a una posición que no existe dentro de una lista o secuencia."
    "KeyError" in output -> "El diccionario no contiene la clave solicitada. Puedes comprobarla con `clave in diccionario` o usar `.get()`."
    "ModuleNotFoundError" in output -> "Ese módulo no está incluido en py.Studio. Los módulos de la biblioteca estándar funcionan; los paquetes externos se añadirán desde el gestor de paquetes."
    "PermissionError" in output -> "Android bloqueó esa ruta. Genera el archivo en el proyecto y usa GUARDAR o COMPARTIR desde py.Studio."
    "URLError" in output || "No address associated with hostname" in output -> "No se pudo alcanzar el servidor. Comprueba Internet, la dirección y que el servicio esté disponible."
    "Traceback" in output -> "Python detuvo la ejecución por un error. La última línea indica el tipo y el mensaje; usa IR A LÍNEA para corregirlo."
    else -> null
}

private inline fun <T> withPythonParameters(parameters: Map<String, String>, block: () -> T): T {
    if (parameters.isEmpty()) return block()
    val environment = requireNotNull(Python.getInstance().getModule("os").get("environ"))
    val previous = parameters.mapValues { (key, _) ->
        if (environment.callAttr("__contains__", key).toBoolean()) environment.callAttr("get", key).toString() else null
    }
    return try {
        parameters.forEach { (key, value) -> environment.callAttr("__setitem__", key, value) }
        block()
    } finally {
        previous.forEach { (key, value) ->
            if (value == null) environment.callAttr("pop", key, null) else environment.callAttr("__setitem__", key, value)
        }
    }
}
