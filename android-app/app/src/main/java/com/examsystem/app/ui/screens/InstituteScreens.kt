@file:OptIn(ExperimentalMaterial3Api::class)

package com.examsystem.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.data.models.AttendanceSession
import com.examsystem.app.ui.theme.RedPrimary
import com.examsystem.app.viewmodel.InstituteViewModel
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val storageDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val displayDateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

private fun todayDateString(): String = storageDateFormat.format(Date())

private fun shiftDateString(date: String, dayDelta: Int): String {
    return try {
        val cal = calendarFromDateString(date) ?: return date
        cal.add(Calendar.DAY_OF_MONTH, dayDelta)
        calendarToDateString(cal)
    } catch (_: Exception) {
        date
    }
}

private fun calendarFromDateString(date: String): Calendar? {
    return try {
        Calendar.getInstance().apply {
            time = storageDateFormat.parse(date) ?: return null
        }
    } catch (_: Exception) {
        null
    }
}

private fun calendarToDateString(cal: Calendar): String = storageDateFormat.format(cal.time)

private fun formatAttendanceDateLabel(date: String, today: String): String {
    val display = try {
        val parsed = storageDateFormat.parse(date) ?: return date
        displayDateFormat.format(parsed)
    } catch (_: Exception) {
        return date
    }
    return when (date) {
        today -> "Today · $display"
        shiftDateString(today, -1) -> "Yesterday · $display"
        else -> display
    }
}

@Composable
private fun AttendanceDatePicker(
    selectedDate: String,
    today: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val label = formatAttendanceDateLabel(selectedDate, today)
    val yesterday = remember(today) { shiftDateString(today, -1) }

    Column(modifier) {
        Text("Date", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val cal = calendarFromDateString(selectedDate) ?: Calendar.getInstance()
                    val todayCal = calendarFromDateString(today) ?: Calendar.getInstance()
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onDateSelected(calendarToDateString(picked))
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).apply {
                        datePicker.maxDate = todayCal.timeInMillis
                    }.show()
                },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDateSelected(shiftDateString(selectedDate, -1)) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = RedPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Tap to pick from calendar", fontSize = 11.sp, color = Color.Gray)
                }
                IconButton(
                    onClick = { onDateSelected(shiftDateString(selectedDate, 1)) },
                    enabled = selectedDate < today
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedDate == yesterday,
                onClick = { onDateSelected(yesterday) },
                label = { Text("Yesterday") },
                leadingIcon = if (selectedDate == yesterday) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = selectedDate == today,
                onClick = { onDateSelected(today) },
                label = { Text("Today") },
                leadingIcon = if (selectedDate == today) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

private fun attendanceSummary(session: AttendanceSession): String {
    val values = session.records.values
    val present = values.count { it == "present" }
    val absent = values.count { it == "absent" }
    val late = values.count { it == "late" }
    return "Present $present · Absent $absent · Late $late"
}

@Composable
fun BatchDetailScreen(
    instituteId: String,
    batchId: String,
    instructorVm: InstructorViewModel,
    instituteVm: InstituteViewModel = viewModel(),
    onBack: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    val studentsState by instituteVm.students.collectAsState()
    val attendance by instituteVm.attendance.collectAsState()
    val attendanceHistory by instituteVm.attendanceHistory.collectAsState()
    val attendanceLoadError by instituteVm.attendanceLoadError.collectAsState()
    val user by instructorVm.currentUser.collectAsState()
    var selectedDate by remember { mutableStateOf(todayDateString()) }
    val today = remember { todayDateString() }
    var localRecords by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showAddStudent by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf("") }
    var studentRoll by remember { mutableStateOf("") }
    val actionState by instituteVm.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val attendanceScroll = rememberScrollState()

    LaunchedEffect(instituteId, batchId) {
        instituteVm.loadStudents(instituteId, batchId)
        instituteVm.loadAttendanceHistory(instituteId, batchId)
    }

    LaunchedEffect(instituteId, batchId, selectedDate) {
        instituteVm.loadAttendance(instituteId, batchId, selectedDate)
    }

    LaunchedEffect(studentsState, attendance, selectedDate) {
        val students = (studentsState as? UiState.Success)?.data ?: return@LaunchedEffect
        val saved = attendance?.records.orEmpty()
        localRecords = students.associate { student ->
            student.studentId to (saved[student.studentId] ?: localRecords[student.studentId] ?: "present")
        }
    }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is UiState.Success -> {
                snackbarHostState.showSnackbar(
                    "Attendance saved for ${formatAttendanceDateLabel(selectedDate, today)}"
                )
                instituteVm.clearActionState()
            }
            is UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                instituteVm.clearActionState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Batch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Students") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Attendance") })
            }
            when (tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { showAddStudent = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add student")
                        }
                    }
                    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                        when (val state = studentsState) {
                            is UiState.Success -> {
                                if (state.data.isEmpty()) {
                                    item {
                                        Text(
                                            "No students yet. Add students here before marking attendance.",
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                items(state.data) { student ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(Modifier.padding(12.dp)) {
                                            Column {
                                                Text(student.name, fontWeight = FontWeight.Bold)
                                                if (student.rollNumber.isNotBlank()) {
                                                    Text(
                                                        "Roll: ${student.rollNumber}",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is UiState.Error -> item { Text(state.message, color = Color.Red) }
                            else -> item { CircularProgressIndicator(color = RedPrimary) }
                        }
                    }
                }
                1 -> {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .verticalScroll(attendanceScroll)
                    ) {
                        Text("Mark attendance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Pick a date, mark each student, then Save.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        AttendanceDatePicker(
                            selectedDate = selectedDate,
                            today = today,
                            onDateSelected = { selectedDate = it },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        attendanceLoadError?.let { err ->
                            Text(err, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        when (val state = studentsState) {
                            is UiState.Success -> {
                                if (state.data.isEmpty()) {
                                    Text(
                                        "No students in this batch. Add students on the Students tab first.",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                } else {
                                    state.data.forEach { student ->
                                        val status = localRecords[student.studentId] ?: "present"
                                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text(student.name, fontWeight = FontWeight.Bold)
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf(
                                                        "present" to "Present",
                                                        "absent" to "Absent",
                                                        "late" to "Late"
                                                    ).forEach { (key, label) ->
                                                        FilterChip(
                                                            selected = status == key,
                                                            onClick = {
                                                                localRecords = localRecords + (student.studentId to key)
                                                            },
                                                            label = { Text(label, fontSize = 11.sp) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val toSave = state.data.associate { student ->
                                                student.studentId to (localRecords[student.studentId] ?: "present")
                                            }
                                            instituteVm.saveAttendance(
                                                instituteId,
                                                batchId,
                                                selectedDate,
                                                toSave,
                                                user?.name ?: user?.email ?: ""
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = actionState !is UiState.Loading,
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                                    ) {
                                        if (actionState is UiState.Loading) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Save attendance")
                                        }
                                    }
                                }
                            }
                            is UiState.Error -> Text(state.message, color = Color.Red, fontSize = 13.sp)
                            else -> CircularProgressIndicator(color = RedPrimary)
                        }

                        Spacer(Modifier.height(20.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))
                        Text("Saved attendance (all dates)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Tap a row to open that day.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        when (val hist = attendanceHistory) {
                            is UiState.Loading -> CircularProgressIndicator(color = RedPrimary)
                            is UiState.Error -> Text(hist.message, color = Color.Red, fontSize = 13.sp)
                            is UiState.Success -> {
                                if (hist.data.isEmpty()) {
                                    Text("No attendance saved yet.", color = Color.Gray, fontSize = 13.sp)
                                } else {
                                    hist.data.forEach { session ->
                                        val isSelected = session.date == selectedDate
                                        Card(
                                            onClick = { selectedDate = session.date },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    RedPrimary.copy(alpha = 0.12f)
                                                } else {
                                                    Color(0xFFF5F5F5)
                                                }
                                            )
                                        ) {
                                            Column(Modifier.padding(14.dp)) {
                                                Text(
                                                    formatAttendanceDateLabel(session.date, today),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (isSelected) RedPrimary else Color.Black
                                                )
                                                Text(attendanceSummary(session), fontSize = 12.sp, color = Color.Gray)
                                                if (session.markedBy.isNotBlank()) {
                                                    Text(
                                                        "Marked by ${session.markedBy}",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    if (showAddStudent) {
        AlertDialog(
            onDismissRequest = { showAddStudent = false },
            title = { Text("Add student") },
            text = {
                Column {
                    OutlinedTextField(
                        studentName, { studentName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        studentRoll, { studentRoll = it },
                        label = { Text("Roll number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    instituteVm.addStudent(instituteId, batchId, studentName, studentRoll)
                    showAddStudent = false
                    studentName = ""
                    studentRoll = ""
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddStudent = false }) { Text("Cancel") } }
        )
    }
}
