package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examsystem.app.data.models.TestRosterStudent
import com.examsystem.app.ui.theme.RedPrimary
import com.examsystem.app.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPickScreen(
    vm: StudentViewModel,
    testId: String,
    roster: List<TestRosterStudent>,
    onContinue: () -> Unit,
    onNewStudent: () -> Unit,
    onBack: () -> Unit
) {
    var selectedId by remember { mutableStateOf(vm.batchStudentId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Who are you?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                text = vm.currentTest?.title ?: "Exam: $testId",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp
            )
            Text(
                "Tap your name — no need to type details again.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.62f),
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roster, key = { it.studentId }) { entry ->
                        val selected = selectedId == entry.studentId
                        Card(
                            onClick = { selectedId = entry.studentId },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) RedPrimary.copy(alpha = 0.12f) else Color(0xFFF5F5F5)
                            )
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = RedPrimary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.Bold)
                                    if (entry.rollNumber.isNotBlank()) {
                                        Text("Roll: ${entry.rollNumber}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = onNewStudent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I'm not on the list")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        roster.find { it.studentId == selectedId }?.let { vm.selectRosterStudent(it) }
                        onContinue()
                    },
                    enabled = selectedId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("CONTINUE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
