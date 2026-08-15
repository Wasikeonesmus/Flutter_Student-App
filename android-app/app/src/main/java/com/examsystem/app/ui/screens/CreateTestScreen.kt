package com.examsystem.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.data.SubscriptionCapabilities
import com.examsystem.app.data.models.Question
import com.examsystem.app.data.models.Section
import com.examsystem.app.data.models.Test
import com.examsystem.app.util.QuestionParser
import com.examsystem.app.ui.components.EditableMathField
import com.examsystem.app.ui.components.MultiBlockMathText
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTestScreen(
    vm: InstructorViewModel = viewModel(),
    editTestId: String? = null,
    onTestCreated: () -> Unit,
    onBack: () -> Unit
) {
    val testsState by vm.tests.collectAsState()
    val editTestFromRepo by vm.editTest.collectAsState()
    val user by vm.currentUser.collectAsState()
    val instructorBatches by vm.instructorBatches.collectAsState()
    val tierCaps = remember(user) {
        SubscriptionCapabilities.fromUser(user)
    }
    val instituteId = user?.instituteId?.trim().orEmpty()
    var limitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadTests() }
    LaunchedEffect(instituteId) {
        if (instituteId.isNotBlank()) vm.loadInstructorBatches(instituteId)
    }

    LaunchedEffect(editTestId) {
        if (!editTestId.isNullOrBlank()) {
            vm.loadTestForEdit(editTestId)
        } else {
            vm.clearEditTest()
        }
    }

    val existingTest = remember(editTestId, testsState, editTestFromRepo) {
        editTestFromRepo
            ?: (testsState as? UiState.Success)?.data?.find { it.testId == editTestId }
    }

    var selectedBatchId by remember(existingTest) { mutableStateOf(existingTest?.batchId ?: "") }
    var batchMenuExpanded by remember { mutableStateOf(false) }

    var title by remember(existingTest) { mutableStateOf(existingTest?.title ?: "") }
    var instructions by remember(existingTest) { mutableStateOf(existingTest?.instructions ?: "") }
    var duration by remember(existingTest) { mutableStateOf(existingTest?.durationMinutes?.toString() ?: "60") }
    var passingMarks by remember(existingTest) { mutableStateOf(existingTest?.passingMarks?.toString() ?: "") }
    var totalMarks by remember(existingTest) { mutableStateOf(existingTest?.totalMarks?.toString() ?: "") }
    var sections by remember(existingTest) {
        val rawSections = existingTest?.sections ?: listOf<Section>()
        val cleaned = rawSections.map { sec ->
            sec.copy(questions = sec.questions.map { q ->
                q.copy(
                    text = q.text.trim(),
                    optionA = q.optionA.replace("\n", " ").replace("\r", " ").trim(),
                    optionB = q.optionB.replace("\n", " ").replace("\r", " ").trim(),
                    optionC = q.optionC.replace("\n", " ").replace("\r", " ").trim(),
                    optionD = q.optionD.replace("\n", " ").replace("\r", " ").trim()
                )
            })
        }
        mutableStateOf(cleaned)
    }
    var releaseScoreMode by remember(existingTest) { mutableStateOf(existingTest?.releaseScoreMode ?: "table_only") }
    var releaseScoreDropdownExpanded by remember { mutableStateOf(false) }
    var acFullscreen by remember(existingTest) { mutableStateOf(existingTest?.antiCheatFullscreen ?: true) }
    var acLeaveApp by remember(existingTest) { mutableStateOf(existingTest?.antiCheatDetectLeaveApp ?: true) }
    var acCopyPaste by remember(existingTest) { mutableStateOf(existingTest?.antiCheatBlockCopyPaste ?: true) }
    var acScreenshot by remember(existingTest) { mutableStateOf(existingTest?.antiCheatBlockScreenshot ?: true) }
    var acCamera by remember(existingTest) { mutableStateOf(existingTest?.antiCheatCamera ?: false) }
    var acRandQ by remember(existingTest) { mutableStateOf(existingTest?.antiCheatRandomizeQuestions ?: true) }
    var acRandO by remember(existingTest) { mutableStateOf(existingTest?.antiCheatRandomizeOptions ?: true) }
    var acAutoSubmit by remember(existingTest) { mutableStateOf(existingTest?.antiCheatAutoSubmit ?: true) }

    var resultReleaseTime by remember(existingTest) {
        mutableStateOf(existingTest?.resultReleaseTime ?: run {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 20)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            com.google.firebase.Timestamp(cal.time)
        })
    }

    var targetSectionIndexForImport by remember { mutableStateOf<Int?>(null) }
    var aiImportText by remember { mutableStateOf("") }
    var selectedAiModel by remember { mutableStateOf("openrouter/auto") }
    var aiModelDropdownExpanded by remember { mutableStateOf(false) }
    var isAiFormatting by remember { mutableStateOf(false) }
    var aiFormatError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val extracted = try {
                    extractTextFromFile(context, uri) ?: ""
                } catch (e: Exception) {
                    "Error: ${e.message ?: "Failed to read file."}"
                }
                if (extracted.startsWith("Error")) {
                    android.widget.Toast.makeText(context, extracted, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    aiImportText = if (aiImportText.isBlank()) extracted.trim() else (aiImportText + "\n\n" + extracted).trim()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
        vm.resetCreateState()
    }
    val createState by vm.createState.collectAsState()

    LaunchedEffect(createState) {
        if (createState is UiState.Success) onTestCreated()
    }

    if (targetSectionIndexForImport != null) {
        AlertDialog(
            onDismissRequest = { targetSectionIndexForImport = null },
            title = { Text("Quick Import & AI Formatter") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Format math and questions with AI using OpenRouter. Paste text below or import a file.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // AI Model Selection
                    ExposedDropdownMenuBox(
                        expanded = aiModelDropdownExpanded,
                        onExpandedChange = { aiModelDropdownExpanded = !aiModelDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAiModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aiModelDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = aiModelDropdownExpanded,
                            onDismissRequest = { aiModelDropdownExpanded = false }
                        ) {
                            listOf(
                                "openrouter/auto" to "Auto-Select Best",
                                "google/gemini-2.5-flash" to "Gemini 2.5 Flash",
                                "meta-llama/llama-3-8b-instruct:free" to "Llama 3 8B (Free)"
                            ).forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedAiModel = id
                                        aiModelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // File Import & Format Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.width(4.dp))
                            Text("PDF/Text/Word", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (aiImportText.isBlank()) {
                                        aiFormatError = "Please enter or upload raw text first."
                                        return@launch
                                    }
                                    isAiFormatting = true
                                    aiFormatError = null
                                    val prompt = """
                                        You are an expert exam formatter. Convert the following raw text or OCR output of exam questions into a standardized list of multiple-choice questions (MCQs).
                                        
                                        Rules:
                                        1. Format each question exactly as:
                                        Question [Number]. [Question Text]
                                        A. [Option A]
                                        B. [Option B]
                                        C. [Option C]
                                        D. [Option D]
                                        ANSWER: [Correct Option Letter, e.g. A or B or C or D]
                                        MARKS: [Number of marks, default to 1]
                                        
                                        2. LaTeX Formatting rules:
                                        - Any math equations, variables, or expressions MUST be properly formatted in standard LaTeX enclosed in single dollar signs (${'$'}...${'$'}) for inline math (e.g. ${'$'}x^2 + y^2 = r^2${'$'}, ${'$'}A = \begin{bmatrix}1&2\\3&4\end{bmatrix}${'$'}), or double dollar signs (${'$'}${'$'}...${'$'}${'$'}) for block math.
                                        - Ensure that row breaks inside LaTeX matrices or systems of equations use proper double backslashes (`\\`).
                                        - Fix any OCR transcription errors in math formulas (e.g. replacing 'x' with \times when it means multiplication, or correcting subscripts/superscripts).
                                        - Standard text should not be wrapped in dollar signs. Only mathematical elements.
                                        
                                        Here is the raw text to convert:
                                        $aiImportText
                                    """.trimIndent()
                                    
                                    val response = vm.formatMcqWithAi(prompt, selectedAiModel)
                                    isAiFormatting = false
                                    if (response.startsWith("Error")) {
                                        aiFormatError = response
                                    } else {
                                        aiImportText = response.trim()
                                    }
                                }
                            },
                            enabled = !isAiFormatting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FlashOn, null)
                            Spacer(Modifier.width(4.dp))
                            Text("AI Format ✨", fontSize = 12.sp)
                        }
                    }

                    if (isAiFormatting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("AI is formatting questions & formulas...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    aiFormatError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = aiImportText,
                        onValueChange = { aiImportText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        placeholder = { Text("Paste MCQ text, import files, or let AI format it...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = QuestionParser.parse(aiImportText)
                        if (parsed.isNotEmpty()) {
                            val currentSections = sections.toMutableList()
                            val sIdx = targetSectionIndexForImport!!
                            if (currentSections.isEmpty()) {
                                currentSections.add(Section(id = UUID.randomUUID().toString(), title = "Section 1", questions = parsed))
                            } else {
                                val target = currentSections[sIdx]
                                currentSections[sIdx] = target.copy(questions = target.questions + parsed)
                            }
                            sections = currentSections
                            aiImportText = ""
                            targetSectionIndexForImport = null
                        }
                    },
                    enabled = !isAiFormatting
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(
                    onClick = { targetSectionIndexForImport = null },
                    enabled = !isAiFormatting
                ) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingTest != null) "Edit Test" else "Create Test") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Test Title") }, modifier = Modifier.fillMaxWidth())
                if (instituteId.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Link to a class batch (optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Students pick their name — no re-typing each exam. New students are added to the batch automatically.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    val batches = (instructorBatches as? UiState.Success)?.data.orEmpty()
                    ExposedDropdownMenuBox(
                        expanded = batchMenuExpanded,
                        onExpandedChange = { batchMenuExpanded = !batchMenuExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = when {
                                selectedBatchId.isBlank() -> "No class link"
                                else -> batches.find { it.batchId == selectedBatchId }?.name ?: "Selected batch"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Class / batch") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(batchMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = batchMenuExpanded,
                            onDismissRequest = { batchMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("No class link") },
                                onClick = {
                                    selectedBatchId = ""
                                    batchMenuExpanded = false
                                }
                            )
                            batches.forEach { batch ->
                                DropdownMenuItem(
                                    text = { Text("${batch.name} (${batch.studentCount} students)") },
                                    onClick = {
                                        selectedBatchId = batch.batchId
                                        batchMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (mins)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = passingMarks, onValueChange = { passingMarks = it }, label = { Text("Passing Marks") }, modifier = Modifier.weight(1f))
                    val calculatedTotalMarks = remember(sections) {
                        sections.sumOf { it.questions.sumOf { q -> q.marks } }
                    }
                    OutlinedTextField(
                        value = calculatedTotalMarks.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Total Marks (Auto)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = releaseScoreDropdownExpanded,
                    onExpandedChange = { releaseScoreDropdownExpanded = !releaseScoreDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = when (releaseScoreMode) {
                            "table_only" -> "Release score table only on app"
                            "full_answers" -> "Release student score with full test (answers)"
                            else -> "Release score table only on app"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Score Release Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(releaseScoreDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = releaseScoreDropdownExpanded,
                        onDismissRequest = { releaseScoreDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Release score table only on app") },
                            onClick = {
                                releaseScoreMode = "table_only"
                                releaseScoreDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Release student score with full test (answers)") },
                            onClick = {
                                releaseScoreMode = "full_answers"
                                releaseScoreDropdownExpanded = false
                            }
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                val format = remember {
                    java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                }
                val releaseTimeStr = remember(resultReleaseTime) {
                    resultReleaseTime?.let { format.format(it.toDate()) } ?: "Immediate"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val calendar = java.util.Calendar.getInstance()
                            resultReleaseTime?.let { calendar.time = it.toDate() } ?: run {
                                calendar.set(java.util.Calendar.HOUR_OF_DAY, 20)
                                calendar.set(java.util.Calendar.MINUTE, 0)
                                calendar.set(java.util.Calendar.SECOND, 0)
                                calendar.set(java.util.Calendar.MILLISECOND, 0)
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    calendar.set(java.util.Calendar.YEAR, year)
                                    calendar.set(java.util.Calendar.MONTH, month)
                                    calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                            calendar.set(java.util.Calendar.MINUTE, minute)
                                            resultReleaseTime = com.google.firebase.Timestamp(calendar.time)
                                        },
                                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                        calendar.get(java.util.Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                ) {
                    OutlinedTextField(
                        value = releaseTimeStr,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Result Release Date & Time") },
                        trailingIcon = {
                            Icon(Icons.Default.Event, contentDescription = "Select Release Time")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sections", style = MaterialTheme.typography.titleMedium)
                    // Input for adding multiple sections at once
                    var multiAddCount by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = multiAddCount,
                            onValueChange = { multiAddCount = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Add #") },
                            placeholder = { Text("e.g., 3") },
                            singleLine = true,
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val count = multiAddCount.toIntOrNull() ?: 0
                                if (count > 0) {
                                    val newSections = mutableListOf<Section>()
                                    for (i in 0 until count) {
                                        val suffix = if (sections.size + newSections.size < 26) {
                                            ('A'.code + sections.size + newSections.size).toChar().toString()
                                        } else {
                                            (sections.size + newSections.size + 1).toString()
                                        }
                                        newSections.add(
                                            Section(
                                                id = UUID.randomUUID().toString(),
                                                title = "Section $suffix"
                                            )
                                        )
                                    }
                                    sections = sections + newSections
                                }
                            },
                            enabled = multiAddCount.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                    // Existing single‑section button
                    FilledTonalButton(onClick = {
                        val suffix = if (sections.size < 26) ('A'.code + sections.size).toChar().toString() else (sections.size + 1).toString()
                        sections = sections + Section(id = UUID.randomUUID().toString(), title = "Section $suffix")
                    }) {
                        Text("Add Section")
                    }
                }
            }

            itemsIndexed(sections) { sIdx, section ->
                SectionEditor(
                    section = section,
                    onSectionUpdated = { updated ->
                        sections = sections.toMutableList().also { it[sIdx] = updated }
                    },
                    onSectionDeleted = {
                        sections = sections.toMutableList().also { it.removeAt(sIdx) }
                    },
                    onQuickPasteClicked = { targetSectionIndexForImport = sIdx }
                )
            }

            item {
                Text("Anti-Cheat (students only)", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (tierCaps.cameraProctoring) {
                        "Applies when students take the exam — not while you create or edit tests. You can still paste and import questions here. Camera proctoring is available on your plan."
                    } else {
                        "Applies when students take the exam — not while you build the test (Quick Paste & AI import stay available). Camera monitoring requires Pro or Institute."
                    },
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AntiCheatSwitch("Full-screen mode", acFullscreen) { acFullscreen = it }
                AntiCheatSwitch("Detect leaving app / tab switch", acLeaveApp) { acLeaveApp = it }
                AntiCheatSwitch("Disable copy & paste (student exam)", acCopyPaste) { acCopyPaste = it }
                AntiCheatSwitch("Block screenshots (warning)", acScreenshot) { acScreenshot = it }
                if (tierCaps.cameraProctoring) {
                    AntiCheatSwitch("Camera monitoring", acCamera) { acCamera = it }
                } else {
                    LaunchedEffect(Unit) { if (acCamera) acCamera = false }
                    Text(
                        "Camera monitoring — upgrade to Pro or Institute to enable.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                AntiCheatSwitch("Randomize question order", acRandQ) { acRandQ = it }
                AntiCheatSwitch("Randomize option order", acRandO) { acRandO = it }
                AntiCheatSwitch("Auto-submit when time ends", acAutoSubmit) { acAutoSubmit = it }
            }

            limitError?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            item {
                Button(
                    onClick = {
                        limitError = null
                        val allTests = (testsState as? UiState.Success)?.data.orEmpty()
                        val (allowed, message) = SubscriptionCapabilities.canCreateNewTest(
                            user,
                            allTests,
                            isEditingExisting = existingTest != null
                        )
                        if (!allowed) {
                            limitError = message
                            return@Button
                        }
                        val cleanedSections = sections.map { sec ->
                            sec.copy(questions = sec.questions.map { q ->
                                q.copy(
                                    text = q.text.trim(),
                                    optionA = q.optionA.replace("\n", " ").replace("\r", " ").trim(),
                                    optionB = q.optionB.replace("\n", " ").replace("\r", " ").trim(),
                                    optionC = q.optionC.replace("\n", " ").replace("\r", " ").trim(),
                                    optionD = q.optionD.replace("\n", " ").replace("\r", " ").trim()
                                )
                            })
                        }
                        val resolvedTestId = existingTest?.testId?.takeIf { it.isNotBlank() }
                            ?: editTestId.orEmpty()
                        val test = Test(
                            testId = resolvedTestId,
                            title = title,
                            instructions = instructions,
                            durationMinutes = duration.toIntOrNull() ?: 60,
                            passingMarks = passingMarks.toIntOrNull() ?: 0,
                            totalMarks = cleanedSections.sumOf { it.questions.sumOf { q -> q.marks } },
                            sections = cleanedSections,
                            releaseScoreMode = releaseScoreMode,
                            resultReleaseTime = resultReleaseTime,
                            resultsReleasedEarly = existingTest?.resultsReleasedEarly ?: false,
                            instructorId = existingTest?.instructorId ?: "",
                            isEnabled = existingTest?.isEnabled ?: true,
                            createdAt = existingTest?.createdAt ?: com.google.firebase.Timestamp.now(),
                            antiCheatFullscreen = acFullscreen,
                            antiCheatDetectLeaveApp = acLeaveApp,
                            antiCheatBlockCopyPaste = acCopyPaste,
                            antiCheatBlockScreenshot = acScreenshot,
                            antiCheatCamera = tierCaps.cameraProctoring && acCamera,
                            antiCheatRandomizeQuestions = acRandQ,
                            antiCheatRandomizeOptions = acRandO,
                            antiCheatAutoSubmit = acAutoSubmit,
                            instituteId = instituteId,
                            batchId = selectedBatchId,
                            roster = emptyList()
                        )
                        if (existingTest != null) vm.updateTest(test) else vm.createTest(test)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = title.isNotBlank()
                ) { Text("Save Test") }
            }
        }
    }
}

@Composable
private fun SectionEditor(section: Section, onSectionUpdated: (Section) -> Unit, onSectionDeleted: () -> Unit, onQuickPasteClicked: () -> Unit) {
    var expanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clipToBounds()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = section.title,
                    onValueChange = { onSectionUpdated(section.copy(title = it)) },
                    label = { Text("Section Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSectionDeleted) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
            
            if (expanded) {
                section.questions.forEachIndexed { qIdx, question ->
                    QuestionEditor(
                        question = question,
                        index = qIdx,
                        onQuestionUpdated = { updated ->
                            val newList = section.questions.toMutableList().also { it[qIdx] = updated }
                            onSectionUpdated(section.copy(questions = newList))
                        },
                        onQuestionDeleted = {
                            val newList = section.questions.toMutableList().also { it.removeAt(qIdx) }
                            onSectionUpdated(section.copy(questions = newList))
                        }
                    )
                }
                Row {
                    TextButton(onClick = {
                        val newList = section.questions + Question(id = UUID.randomUUID().toString())
                        onSectionUpdated(section.copy(questions = newList))
                    }) { Text("+ Add Question") }
                    TextButton(onClick = onQuickPasteClicked) { Text("Quick Paste") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionEditor(question: Question, index: Int, onQuestionUpdated: (Question) -> Unit, onQuestionDeleted: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Q${index + 1}: ", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (question.text.isBlank()) "(Empty Question)" else {
                            val cleanText = com.examsystem.app.util.MathUtils.stripLatex(question.text)
                            if (cleanText.length > 60) cleanText.take(60) + "..." else cleanText
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row {
                    IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
                    IconButton(onClick = onQuestionDeleted) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            if (expanded) {
                EditableMathField(
                    value = question.text,
                    onValueChange = { onQuestionUpdated(question.copy(text = it)) },
                    label = "Question Text",
                    minLines = 2,
                    placeholder = "Tap to type. Words plain; math in \$...\$ e.g. \$2x+5=11\$ or matrix \\begin{bmatrix}...\\end{bmatrix}"
                )
                Spacer(Modifier.height(8.dp))
                EditableMathField(
                    value = question.optionA,
                    onValueChange = { onQuestionUpdated(question.copy(optionA = it.replace("\n", " ").replace("\r", ""))) },
                    label = "Option A"
                )
                Spacer(Modifier.height(8.dp))
                EditableMathField(
                    value = question.optionB,
                    onValueChange = { onQuestionUpdated(question.copy(optionB = it.replace("\n", " ").replace("\r", ""))) },
                    label = "Option B"
                )
                Spacer(Modifier.height(8.dp))
                EditableMathField(
                    value = question.optionC,
                    onValueChange = { onQuestionUpdated(question.copy(optionC = it.replace("\n", " ").replace("\r", ""))) },
                    label = "Option C"
                )
                Spacer(Modifier.height(8.dp))
                EditableMathField(
                    value = question.optionD,
                    onValueChange = { onQuestionUpdated(question.copy(optionD = it.replace("\n", " ").replace("\r", ""))) },
                    label = "Option D"
                )
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "Correct: ${question.correctAnswer}",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            listOf("A", "B", "C", "D").forEach { opt ->
                                DropdownMenuItem(text = { Text("Option $opt") }, onClick = { onQuestionUpdated(question.copy(correctAnswer = opt)); dropdownExpanded = false })
                            }
                        }
                    }

                    OutlinedTextField(
                        value = question.marks.toString(),
                        onValueChange = { newVal ->
                            val cleanVal = newVal.filter { it.isDigit() }
                            val parsedMarks = if (cleanVal.isEmpty()) 1 else cleanVal.toIntOrNull() ?: 1
                            onQuestionUpdated(question.copy(marks = parsedMarks))
                        },
                        label = { Text("Marks") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clipToBounds(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Full preview (question + all options)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "How students will see this question",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        // Single WebView renders all 5 blocks at once — eliminates the async
                        // height-race that caused equations to overflow into adjacent rows.
                        MultiBlockMathText(
                            blocks = listOf(
                                "Q:" to question.text,
                                "A:" to question.optionA,
                                "B:" to question.optionB,
                                "C:" to question.optionC,
                                "D:" to question.optionD
                            ),
                            textSizeSp = 15
                        )
                    }
                }
            }
        }
    }
}

suspend fun extractTextFromFile(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""

        val isPdf  = mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)
        val isDocx = mimeType.contains("wordprocessingml", ignoreCase = true) || uri.toString().endsWith(".docx", ignoreCase = true)

        if (isPdf) {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext "Error: Could not open PDF stream."
            try {
                val document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper()
                val text = try { stripper.getText(document) ?: "" } catch (e: Exception) { "" }
                document.close()
                text
            } catch (e: Exception) {
                "Error: Could not read PDF — ${e.message ?: "file may be corrupted or password-protected."}."
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }
        } else if (isDocx) {
            // Copy URI stream into a temp seekable file so ZipFile can read the
            // central directory from the end — solves "zip END header not found".
            val tempFile = java.io.File.createTempFile("docx_import", ".docx", context.cacheDir)
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: return@withContext "Error: Could not open DOCX stream."
                input.use { src -> tempFile.outputStream().use { dst -> src.copyTo(dst) } }
                extractTextFromDocx(tempFile)
            } finally {
                try { tempFile.delete() } catch (_: Exception) {}
            }
        } else {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext "Error: Could not open file stream."
            inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: java.util.zip.ZipException) {
        "Error: Invalid or corrupted DOCX file (ZIP header mismatch)."
    } catch (e: NullPointerException) {
        "Error: File could not be read — internal null reference (file may be empty or corrupted)."
    } catch (e: Exception) {
        "Error: ${e.message ?: "Failed to extract text."}"
    }
}

fun extractTextFromDocx(file: java.io.File): String {
    // ZipFile reads central directory from the END of the archive — always reliable.
    // Wrapped in try-finally to guarantee closure even if XML parsing throws.
    val zipFile = try {
        java.util.zip.ZipFile(file)
    } catch (e: Exception) {
        return "Error: Could not open DOCX file as ZIP — ${e.message ?: "file may be corrupted."}."
    }
    return try {
        val entry = zipFile.getEntry("word/document.xml")
            ?: return "Error: Not a valid DOCX file (word/document.xml is missing)."
        val sb = StringBuilder()
        zipFile.getInputStream(entry).use { inputStream ->
            val xmlContent = inputStream.bufferedReader(Charsets.UTF_8).readText()
            // Replace paragraph markers with newlines to keep structure
            val cleanXml = xmlContent.replace("<w:p>", "\n").replace("<w:p ", "\n")
            // Find all <w:t> text elements; group(1) guarded against null (non-participating groups)
            val matcher = java.util.regex.Pattern.compile("<w:t[^>]*>(.*?)</w:t>").matcher(cleanXml)
            while (matcher.find()) {
                val captured = matcher.group(1) ?: continue   // ← null-safe guard
                sb.append(captured)
            }
        }
        // Decode HTML entities that Word XML-encodes
        sb.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    } catch (e: NullPointerException) {
        "Error: DOCX internal structure is null — file may be empty or corrupted."
    } catch (e: Exception) {
        "Error: Failed to parse DOCX — ${e.message ?: "unknown error."}."
    } finally {
        try { zipFile.close() } catch (_: Exception) {}
    }
}

@Composable
private fun AntiCheatSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
