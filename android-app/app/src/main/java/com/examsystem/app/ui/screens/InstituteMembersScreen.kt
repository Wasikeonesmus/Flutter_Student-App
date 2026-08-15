@file:OptIn(ExperimentalMaterial3Api::class)

package com.examsystem.app.ui.screens

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
import com.examsystem.app.data.models.InstituteMember
import com.examsystem.app.ui.theme.RedPrimary
import com.examsystem.app.viewmodel.InstituteViewModel
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState

@Composable
fun InstituteMembersScreen(
    instructorVm: InstructorViewModel,
    instituteVm: InstituteViewModel = viewModel(),
    onBack: () -> Unit
) {
    val institute by instituteVm.institute.collectAsState()
    val membersState by instituteVm.members.collectAsState()
    val actionState by instituteVm.actionState.collectAsState()
    val user by instructorVm.currentUser.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { instituteVm.loadInstitute(it) }
    }

    LaunchedEffect(institute?.instituteId) {
        institute?.instituteId?.let { instituteVm.loadMembers(it) }
    }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is UiState.Success -> {
                if (showAdd) {
                    showAdd = false
                    email = ""
                }
                snackbarHostState.showSnackbar("Instructor added successfully")
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
                title = { Text("Instructors") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (instituteVm.isOwner) {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (institute == null) {
                item {
                    Text(
                        "Academy not loaded. Go back to Institute Dashboard and tap Retry.",
                        color = Color.Gray
                    )
                }
                return@LazyColumn
            }
            if (!instituteVm.isOwner) {
                item {
                    Text(
                        "Only the academy owner can add instructors.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            when (val state = membersState) {
                is UiState.Loading -> item { CircularProgressIndicator(color = RedPrimary) }
                is UiState.Error -> item { Text(state.message, color = Color.Red) }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            Text(
                                "No instructors yet. Owner taps + and enters their email (they must register in the app and be approved by Super Admin first).",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                    items(state.data) { member ->
                        MemberRow(
                            member = member,
                            canRemove = instituteVm.isOwner && member.role != "owner",
                            onRemove = {
                                institute?.instituteId?.let { iid ->
                                    user?.uid?.let { ownerUid ->
                                        instituteVm.removeMember(iid, member.uid, ownerUid)
                                    }
                                }
                            }
                        )
                    }
                }
                else -> {}
            }
        }
    }

    if (showAdd) {
        val adding = actionState is UiState.Loading
        var newName by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!adding) showAdd = false },
            title = { Text("Add instructor") },
            text = {
                Column {
                    Text(
                        "Enter the instructor's name, email, and set a password they will use to sign in.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !adding,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email address") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !adding,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Set password (min 6 chars)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !adding,
                        singleLine = true,
                        visualTransformation = if (passwordVisible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )
                    if (actionState is UiState.Error && showAdd) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (actionState as UiState.Error).message,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val iid = institute?.instituteId ?: return@TextButton
                        instituteVm.addMemberWithCredentials(iid, newName, email, newPassword)
                    },
                    enabled = !adding &&
                            newName.trim().isNotBlank() &&
                            email.trim().contains("@") &&
                            newPassword.length >= 6
                ) {
                    if (adding) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!adding) {
                        showAdd = false
                        email = ""
                    }
                }) { Text("Cancel") }
            }
        )
    }

}

@Composable
private fun MemberRow(member: InstituteMember, canRemove: Boolean, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(member.name.ifBlank { member.email }, fontWeight = FontWeight.Bold)
                Text(member.email, fontSize = 12.sp, color = Color.Gray)
                Text(member.role.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = RedPrimary)
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                }
            }
        }
    }
}
