@file:OptIn(ExperimentalMaterial3Api::class)

package com.examsystem.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.data.models.Batch
import com.examsystem.app.ui.theme.RedPrimary
import com.examsystem.app.viewmodel.InstituteViewModel
import com.examsystem.app.viewmodel.UiState

@Composable
fun InstituteBatchesScreen(
    instituteVm: InstituteViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenBatch: (String, String) -> Unit
) {
    val institute by instituteVm.institute.collectAsState()
    val batchesState by instituteVm.batches.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var batchToDelete by remember { mutableStateOf<Batch?>(null) }
    var batchName by remember { mutableStateOf("") }
    var batchDesc by remember { mutableStateOf("") }

    LaunchedEffect(institute?.instituteId) {
        institute?.instituteId?.let { instituteVm.loadBatches(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batches") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create batch")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (institute == null) {
                item {
                    Text("Academy not loaded. Go back and open Institute Dashboard first.", color = Color.Gray)
                }
                return@LazyColumn
            }
            when (val state = batchesState) {
                is UiState.Loading -> item { CircularProgressIndicator(color = RedPrimary) }
                is UiState.Error -> item { Text(state.message, color = Color.Red) }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item { Text("No batches yet. Tap + to create a class/group.", color = Color.Gray) }
                    }
                    items(state.data) { batch ->
                        BatchRow(
                            batch = batch,
                            onClick = { onOpenBatch(institute!!.instituteId, batch.batchId) },
                            onDelete = { batchToDelete = batch }
                        )
                    }
                }
                else -> {}
            }
        }
    }

    if (batchToDelete != null && institute != null) {
        val batch = batchToDelete!!
        AlertDialog(
            onDismissRequest = { batchToDelete = null },
            title = { Text("Delete batch?") },
            text = {
                Text(
                    "Delete \"${batch.name}\"? Students and attendance for this batch will be removed.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    instituteVm.deleteBatch(institute!!.instituteId, batch.batchId)
                    batchToDelete = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { batchToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showCreate && institute != null) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New batch") },
            text = {
                Column {
                    OutlinedTextField(
                        batchName, { batchName = it },
                        label = { Text("Batch name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        batchDesc, { batchDesc = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    instituteVm.createBatch(institute!!.instituteId, batchName, batchDesc)
                    showCreate = false
                    batchName = ""
                    batchDesc = ""
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BatchRow(batch: Batch, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(batch.name, fontWeight = FontWeight.Bold)
                if (batch.description.isNotBlank()) {
                    Text(batch.description, fontSize = 12.sp, color = Color.Gray)
                }
                Text("${batch.studentCount} students", fontSize = 12.sp, color = RedPrimary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete batch", tint = Color.Red)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
