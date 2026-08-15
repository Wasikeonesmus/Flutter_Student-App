package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examsystem.app.ui.theme.*
import com.examsystem.app.viewmodel.StudentAttemptItem
import com.examsystem.app.viewmodel.StudentViewModel
import com.examsystem.app.viewmodel.UiState
import com.examsystem.app.util.ResultsRelease
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAttemptsScreen(
    vm: StudentViewModel,
    onNavigateToResults: () -> Unit,
    onBack: () -> Unit
) {
    val myAttemptsState by vm.myAttempts.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadMyAttempts()
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
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Completed Tests",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Main Card Content
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                when (val state = myAttemptsState) {
                    is UiState.Idle, is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RedPrimary)
                        }
                    }
                    is UiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                color = Color.Black,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { vm.loadMyAttempts() },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                            ) {
                                Text("RETRY", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is UiState.Success -> {
                        val items = state.data
                        if (items.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AssignmentTurnedIn,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "No past exam attempts found on this device.",
                                    color = Color.Gray,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(items) { item ->
                                    AttemptItemCard(item = item, onClick = {
                                        // Reconstruct the Test object for SubmitSuccessScreen
                                        val testObj = com.examsystem.app.data.models.Test(
                                            testId = item.attempt.testId,
                                            title = item.testTitle,
                                            totalMarks = item.totalMarks,
                                            passingMarks = item.passingMarks,
                                            releaseScoreMode = item.releaseScoreMode,
                                            resultReleaseTime = item.resultReleaseTime,
                                            resultsReleasedEarly = item.resultsReleasedEarly
                                        )
                                        vm.currentTest = testObj
                                        vm.lastAttempt = item.attempt
                                        vm.startObservingAttempt(item.attempt.attemptId)
                                        onNavigateToResults()
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttemptItemCard(
    item: StudentAttemptItem,
    onClick: () -> Unit
) {
    val attempt = item.attempt
    val isReleased = remember(item) {
        val testObj = com.examsystem.app.data.models.Test(
            testId = attempt.testId,
            title = item.testTitle,
            totalMarks = item.totalMarks,
            passingMarks = item.passingMarks,
            releaseScoreMode = item.releaseScoreMode,
            resultReleaseTime = item.resultReleaseTime,
            resultsReleasedEarly = item.resultsReleasedEarly
        )
        ResultsRelease.isReleased(testObj)
    }

    val date = attempt.submittedAt?.toDate() ?: Date()
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val formattedDate = sdf.format(date)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.testTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Submitted: $formattedDate",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Score or Locked Badge
                if (!isReleased) {
                    Badge(
                        containerColor = Color.LightGray,
                        contentColor = Color.DarkGray
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LOCKED", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                } else {
                    val passingMarks = item.passingMarks
                    val passed = attempt.totalScore >= passingMarks
                    val pct = if (item.totalMarks > 0) (attempt.totalScore * 100) / item.totalMarks else 0
                    
                    Badge(
                        containerColor = if (passed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        contentColor = if (passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    ) {
                        Text(
                            text = if (passed) "PASSED" else "FAILED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Score:",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
                if (!isReleased) {
                    Text(
                        text = "Score Hidden",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                } else {
                    val pct = if (item.totalMarks > 0) (attempt.totalScore * 100) / item.totalMarks else 0
                    Text(
                        text = "${attempt.totalScore} / ${item.totalMarks} ($pct%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
