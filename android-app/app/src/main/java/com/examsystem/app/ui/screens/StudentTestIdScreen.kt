package com.examsystem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.examsystem.app.ui.theme.*

import com.examsystem.app.util.ExamSchedule
import com.examsystem.app.viewmodel.StudentViewModel
import com.examsystem.app.viewmodel.UiState

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentTestIdScreen(
    vm: StudentViewModel,
    onTestFound: (String) -> Unit,
    onAlreadySubmitted: () -> Unit,
    onBack: () -> Unit
) {
    var testId by remember { mutableStateOf("") }
    val testState by vm.testState.collectAsState()
    var isCheckingAttempt by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Clear any previous test state when this screen appears
    // (prevents auto-navigation if student previously found an exam)
    LaunchedEffect(Unit) {
        vm.resetState()
        vm.loadPlatformSettings()
    }

    LaunchedEffect(testState) {
        val state = testState
        if (state is UiState.Success) {
            isCheckingAttempt = true
            val test = state.data
            // Must await — checkExistingAttempt is suspend; Deferred was always non-null before
            val existing = vm.checkExistingAttempt(test.testId)
            isCheckingAttempt = false
            if (existing != null) {
                // Same phone already submitted this exam — show results, not the exam form
                onAlreadySubmitted()
            } else {
                onTestFound(test.testId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RedPrimary, Color.Black)
                )
            )
    ) {
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Top Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Join Exam",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enter the ID to begin",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp
            )
        }

        // Bottom Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(350.dp),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                
                TextField(
                    value = testId,
                    onValueChange = { 
                        testId = it.uppercase() 
                        localError = null
                        if (testState is UiState.Error) {
                            vm.resetState()
                        }
                    }, // Auto-uppercase to match exam IDs
                    label = { Text("Unique Test ID") },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.LightGray,
                        focusedIndicatorColor = RedPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val errorMsg = remember(testState, localError) {
                    when {
                        localError != null -> localError
                        testState is UiState.Error -> (testState as UiState.Error).message
                        else -> null
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                val isLoading = testState is UiState.Loading || isCheckingAttempt

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (!ExamSchedule.isPortalOpenPkt()) {
                            localError = ExamSchedule.portalClosedMessage()
                        } else {
                            localError = null
                            vm.fetchTest(testId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = testId.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("FIND EXAM", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
