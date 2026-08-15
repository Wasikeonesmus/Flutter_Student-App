@file:OptIn(ExperimentalMaterial3Api::class)

package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.ui.theme.RedPrimary
import com.examsystem.app.viewmodel.InstituteViewModel
import com.examsystem.app.viewmodel.InstructorViewModel

@Composable
fun InstituteDashboardScreen(
    instructorVm: InstructorViewModel,
    instituteVm: InstituteViewModel = viewModel(),
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onBatches: () -> Unit
) {
    val user by instructorVm.currentUser.collectAsState()
    val institute by instituteVm.institute.collectAsState()
    val stats by instituteVm.stats.collectAsState()
    val instituteLoading by instituteVm.instituteLoading.collectAsState()
    val instituteError by instituteVm.instituteError.collectAsState()

    LaunchedEffect(user?.uid) {
        user?.uid?.let { instituteVm.loadInstitute(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Institute Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RedPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (instituteLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
            return@Scaffold
        }

        if (institute == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.School, null, tint = RedPrimary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        instituteError ?: "Academy not linked yet.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = Color.DarkGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Need Institute plan approved by admin. Then tap Retry.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { user?.uid?.let { instituteVm.loadInstitute(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) { Text("Retry setup") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F8F8)),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(RedPrimary, Color(0xFFB71C1C))))
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(institute!!.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (instituteVm.isOwner) "Academy owner" else "Institute instructor",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InstituteStatTile("Instructors", "${stats["instructors"] ?: 0}", Modifier.weight(1f))
                    InstituteStatTile("Batches", "${stats["batches"] ?: 0}", Modifier.weight(1f))
                    InstituteStatTile("Students", "${stats["students"] ?: 0}", Modifier.weight(1f))
                }
            }
            item {
                Text("Manage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            item {
                InstituteNavCard(
                    title = "Instructors",
                    subtitle = if (instituteVm.isOwner) "Add or remove teaching staff" else "View institute staff",
                    icon = Icons.Default.Groups,
                    onClick = onMembers
                )
            }
            item {
                InstituteNavCard(
                    title = "Batches",
                    subtitle = "Classes, groups, and student rosters",
                    icon = Icons.Default.Class,
                    onClick = onBatches
                )
            }
            item {
                InstituteNavCard(
                    title = "Attendance",
                    subtitle = "Batches → open a class → Attendance tab",
                    icon = Icons.Default.EventAvailable,
                    onClick = onBatches
                )
            }
        }
    }
}

@Composable
private fun InstituteStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = RedPrimary)
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun InstituteNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
