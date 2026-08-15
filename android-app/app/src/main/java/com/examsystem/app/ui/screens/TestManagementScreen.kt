package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.data.models.Test
import com.examsystem.app.ui.theme.*
import com.examsystem.app.util.PdfGenerator
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestManagementScreen(
    vm: InstructorViewModel = viewModel(),
    onViewResults: (String) -> Unit,
    onEditTest: (String) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { vm.loadTests() }
    val testsState by vm.tests.collectAsState()
    val attemptsCountMap by vm.attemptsCountMap.collectAsState()
    var deleteDialogTest by remember { mutableStateOf<Test?>(null) }

    if (deleteDialogTest != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogTest = null },
            title = { Text("Delete Test", fontWeight = FontWeight.Bold) },
            text  = { Text("Delete \"${deleteDialogTest?.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTest(deleteDialogTest!!.testId)
                    deleteDialogTest = null
                }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogTest = null }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("My Tests", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // Bottom white card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            when (val state = testsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary)
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.message, color = Color.Red, fontSize = 14.sp)
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Description, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                                Spacer(Modifier.height(12.dp))
                                Text("No tests yet", fontSize = 16.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = 36.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(state.data) { test ->
                                val count = attemptsCountMap[test.testId] ?: 0
                                StyledTestCard(
                                    test = test,
                                    attemptsCount = count,
                                    onToggle  = { vm.toggleTest(test.testId, !test.isEnabled) },
                                    onDelete  = { deleteDialogTest = test },
                                    onViewResults = { onViewResults(test.testId) },
                                    onEdit = { onEditTest(test.testId) }
                                )
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledTestCard(
    test: Test,
    attemptsCount: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onViewResults: () -> Unit,
    onEdit: () -> Unit
) {
    var showTestId by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }
    val createdDateStr = remember(test.createdAt) {
        test.createdAt?.toDate()?.let { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(it) } ?: "Recent"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(test.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (test.isEnabled) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ) {
                            Text(
                                if (test.isEnabled) "Active" else "Off",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = if (test.isEnabled) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Created: $createdDateStr  •  $attemptsCount Student Attempts",
                        fontSize = 12.sp, color = RedPrimary, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${test.sections.size} sections  •  ${test.durationMinutes} mins  •  ${test.totalMarks} marks",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                Switch(
                    checked = test.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RedPrimary)
                )
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = Color(0xFFEEEEEE))
            Spacer(Modifier.height(10.dp))

            // Test ID row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ID: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (showTestId) test.testId else "••••••••",
                    fontSize = 13.sp, color = RedPrimary, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { showTestId = !showTestId }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (showTestId) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(test.testId)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(17.dp), tint = Color.Gray)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onViewResults,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Icon(Icons.Default.BarChart, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Results", fontSize = 13.sp)
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4A6A))
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isGeneratingPdf = true
                            PdfGenerator.generateTestPdf(context, test)
                            isGeneratingPdf = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = !isGeneratingPdf
                ) {
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontSize = 13.sp)
                }
            }
        }
    }
}
