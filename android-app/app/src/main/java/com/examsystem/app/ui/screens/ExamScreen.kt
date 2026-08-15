package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import com.examsystem.app.ui.components.DraggableExamCameraOverlay
import com.examsystem.app.ui.components.SmartMathText
import com.examsystem.app.util.AntiCheatConfig
import com.examsystem.app.util.AntiCheatViolation
import com.examsystem.app.util.ExamAntiCheatEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class Question(val id: String, val text: String, val imageUrl: String = "", val options: List<Option>)
data class Option(val label: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExamScreen(
    testTitle: String,
    questions: List<Question>,
    timeDurationMins: Int,
    antiCheat: AntiCheatConfig = AntiCheatConfig(),
    cheatAlertCount: Int = 0,
    initialAnswers: Map<String, String> = emptyMap(),
    onProgressUpdate: (Map<String, String>) -> Unit = {},
    onCheatDetected: (AntiCheatViolation) -> Unit = {},
    onSubmit: (Map<String, String>) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { questions.size })
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var answers by remember { mutableStateOf(initialAnswers.toMutableMap()) }
    var flaggedQuestions by remember { mutableStateOf(mutableSetOf<String>()) }
    var timeLeftSeconds by remember { mutableIntStateOf(timeDurationMins * 60) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var showNavGrid by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var showTimeUpDialog by remember { mutableStateOf(false) }
    var lastViolationMessage by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState()

    fun submitOnce(final: Map<String, String>) {
        if (submitted) return
        submitted = true
        onSubmit(final)
    }

    fun onViolation(v: AntiCheatViolation) {
        lastViolationMessage = when (v) {
            AntiCheatViolation.SCREENSHOT_BLOCKED -> "Screenshots are disabled during this exam."
            AntiCheatViolation.LEFT_APP -> "Do not leave the exam. Tab switching is recorded."
            AntiCheatViolation.SPLIT_SCREEN -> "Split-screen is not allowed during the exam."
            AntiCheatViolation.CAMERA_OFF -> "Camera monitoring is required for this exam."
        }
        onCheatDetected(v)
    }

    ExamAntiCheatEffect(config = antiCheat, onViolation = ::onViolation)

    LaunchedEffect(Unit) {
        while (timeLeftSeconds > 0 && !submitted) {
            delay(1000L)
            timeLeftSeconds--
        }
        if (!submitted && antiCheat.autoSubmitOnTimeout) {
            showTimeUpDialog = true
            delay(800L)
            submitOnce(answers)
        }
    }

    val formatTime = { seconds: Int ->
        val m = seconds / 60
        val s = seconds % 60
        String.format("%02d:%02d", m, s)
    }

    val noCopyModifier = if (antiCheat.blockCopyPaste) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { /* block long-press copy */ })
        }
    } else Modifier

    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(testTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                if (cheatAlertCount > 0) {
                                    Text(
                                        "Anti-cheat warnings: $cheatAlertCount",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { showNavGrid = true }) {
                                Icon(Icons.Default.GridView, "Grid")
                            }
                        },
                        actions = {
                            if (antiCheat.fullscreen) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    modifier = Modifier.padding(end = 4.dp).size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .background(
                                        color = if (timeLeftSeconds < 60) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(formatTime(timeLeftSeconds), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                                enabled = pagerState.currentPage > 0 && !submitted
                            ) { Text("Back") }

                            if (pagerState.currentPage < questions.size - 1) {
                                Button(
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                                    enabled = !submitted
                                ) { Text("Next") }
                            } else {
                                Button(onClick = { showSubmitDialog = true }, enabled = !submitted) {
                                    Text("Finish Exam")
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Column(Modifier.padding(paddingValues)) {
                    if (antiCheat.blockScreenshot || antiCheat.detectLeaveApp) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    buildString {
                                        append("Secure exam: ")
                                        if (antiCheat.fullscreen) append("fullscreen · ")
                                        if (antiCheat.blockScreenshot) append("no screenshots · ")
                                        if (antiCheat.detectLeaveApp) append("no app switching")
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    lastViolationMessage?.let { msg ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                msg,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        beyondBoundsPageCount = 0,
                        userScrollEnabled = !submitted
                    ) { page ->
                        val question = questions[page]
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = 600.dp)
                                    .padding(12.dp)
                                    .then(noCopyModifier),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Question ${page + 1}/${questions.size}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (flaggedQuestions.contains(question.id)) {
                                            Icon(
                                                Icons.Filled.Flag,
                                                null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = (page + 1).toFloat() / questions.size,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SmartMathText(text = question.text, textSizeSp = 20, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(24.dp))
                                    question.options.forEach { option ->
                                        val isSelected = answers[question.id] == option.label
                                        OptionCard(
                                            label = option.label,
                                            text = option.text,
                                            isSelected = isSelected,
                                            onClick = {
                                                if (submitted) return@OptionCard
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val newMap = answers.toMutableMap()
                                                newMap[question.id] = option.label
                                                answers = newMap
                                                onProgressUpdate(newMap)
                                            }
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            val newFlags = flaggedQuestions.toMutableSet()
                                            if (newFlags.contains(question.id)) newFlags.remove(question.id)
                                            else newFlags.add(question.id)
                                            flaggedQuestions = newFlags
                                        },
                                        modifier = Modifier.align(Alignment.End).padding(top = 16.dp)
                                    ) {
                                        Icon(
                                            if (flaggedQuestions.contains(question.id)) Icons.Filled.Flag else Icons.Outlined.Flag,
                                            null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (flaggedQuestions.contains(question.id)) "Unflag" else "Flag for Review")
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (antiCheat.cameraMonitoring) {
                DraggableExamCameraOverlay(
                    enabled = true,
                    onViolation = ::onViolation
                )
            }
        }
    }

    if (antiCheat.blockCopyPaste) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = Color.Transparent
            )
        ) { content() }
    } else {
        content()
    }

    if (showNavGrid) {
        ModalBottomSheet(onDismissRequest = { showNavGrid = false }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.6f)) {
                Text("Jump to Question", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(questions) { index, q ->
                        val isAnswered = answers.containsKey(q.id)
                        val isFlagged = flaggedQuestions.contains(q.id)
                        val isCurrent = pagerState.currentPage == index
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(index)
                                    showNavGrid = false
                                }
                            },
                            shape = CircleShape,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isFlagged -> MaterialTheme.colorScheme.errorContainer
                                isAnswered -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (index + 1).toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                                        isFlagged -> MaterialTheme.colorScheme.onErrorContainer
                                        isAnswered -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showSubmitDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitDialog = false },
            title = { Text("Submit Exam?") },
            text = {
                Column {
                    Text("You have answered ${answers.size} out of ${questions.size} questions.")
                    if (cheatAlertCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Anti-cheat warnings recorded: $cheatAlertCount",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { submitOnce(answers) }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { showSubmitDialog = false }) { Text("Cancel") } }
        )
    }

    if (showTimeUpDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Time's Up") },
            text = { Text("The exam timer reached 00:00. Your answers are being submitted automatically.") },
            confirmButton = {}
        )
    }
}

@Composable
fun OptionCard(label: String, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            SmartMathText(text = text, textSizeSp = 16, modifier = Modifier.weight(1f))
        }
    }
}
