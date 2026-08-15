package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examsystem.app.ui.theme.*
import com.examsystem.app.util.AntiCheatConfig

@Composable
fun InstructionsScreen(
    testTitle: String = "Final Assessment",
    totalQuestions: Int = 50,
    timeDurationMins: Int = 60,
    antiCheat: AntiCheatConfig = AntiCheatConfig(),
    onStartExam: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = testTitle,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Exam Instructions",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )
            Spacer(Modifier.height(16.dp))

            // Stats strip
            Row(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                InfoChip(label = "$totalQuestions Questions")
                InfoChip(label = "$timeDurationMins Minutes")
            }
        }

        // Bottom white card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.68f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Before You Begin", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                val instructions = buildList {
                    add("The timer starts immediately after you tap 'Start Exam'.")
                    if (antiCheat.detectLeaveApp) {
                        add("Stay in the app — leaving, split-screen, or switching apps is recorded.")
                    }
                    if (antiCheat.fullscreen || antiCheat.blockScreenshot || antiCheat.blockCopyPaste) {
                        val parts = buildList {
                            if (antiCheat.fullscreen) add("full-screen")
                            if (antiCheat.blockScreenshot) add("screenshots blocked")
                            if (antiCheat.blockCopyPaste) add("copy/paste disabled")
                        }
                        add("Secure mode: ${parts.joinToString(", ")}.")
                    }
                    if (antiCheat.randomizeQuestions || antiCheat.randomizeOptions) {
                        add("Questions and answer options may appear in random order.")
                    }
                    if (antiCheat.autoSubmitOnTimeout) {
                        add("The exam auto-submits when the timer reaches 00:00.")
                    }
                    if (antiCheat.cameraMonitoring) {
                        add("Camera monitoring is ON — allow camera permission when prompted.")
                    }
                    add("Results will be shared by your instructor after review.")
                }
                val showLeaveWarning = antiCheat.detectLeaveApp
                instructions.forEachIndexed { index, text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(RedPrimary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedPrimary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(text, fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (showLeaveWarning) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Do not switch apps or split screen. Violations are recorded on your attempt.",
                                color = Color(0xFFE65100),
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                } else {
                    Spacer(Modifier.height(28.dp))
                }

                Button(
                    onClick = onStartExam,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("START EXAM NOW", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}
