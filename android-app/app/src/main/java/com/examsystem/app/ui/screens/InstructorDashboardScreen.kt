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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.data.SubscriptionCapabilities
import com.examsystem.app.ui.theme.*
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorDashboardScreen(
    vm: InstructorViewModel = viewModel(),
    onCreateTest: () -> Unit,
    onManageTests: () -> Unit,
    onViewResults: () -> Unit,
    onSwitchToStudent: () -> Unit,
    onOpenInstitute: () -> Unit = {},
    onLogout: () -> Unit
) {
    LaunchedEffect(Unit) {
        vm.loadStats()
        vm.loadTests()
    }

    val stats by vm.stats.collectAsState()
    val testsState by vm.tests.collectAsState()
    val user by vm.currentUser.collectAsState()
    val tierCaps = remember(user) {
        SubscriptionCapabilities.fromUser(user)
    }
    val showInstitute = tierCaps.instituteHub || user?.instituteId?.isNotBlank() == true
    val testsThisMonth = remember(testsState) {
        (testsState as? UiState.Success)?.data?.let { SubscriptionCapabilities.countTestsCreatedThisMonth(it) } ?: 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ExamPro", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Hello, ${user?.name ?: "Instructor"}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        tierCaps.tier.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row {
                IconButton(onClick = onSwitchToStudent) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Student Mode", tint = Color.White)
                }
                IconButton(onClick = { vm.logout(); onLogout() }) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                }
            }
        }

        // Stats strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .align(Alignment.TopCenter)
                .padding(top = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniStatCard("Tests",    "${stats["totalTests"]    ?: 0}", Icons.Default.Description, Modifier.weight(1f))
            MiniStatCard("Active",   "${stats["activeExams"]   ?: 0}", Icons.Default.PlayArrow,   Modifier.weight(1f))
            MiniStatCard("Students", "${stats["totalAttempts"] ?: 0}", Icons.Default.People,      Modifier.weight(1f))
        }

        // Bottom white card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.62f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Actions
                item {
                    Text("Quick Actions", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (tierCaps.maxTestsPerMonth != null) {
                        Text(
                            "Tests this month: $testsThisMonth / ${tierCaps.maxTestsPerMonth} (Basic plan)",
                            fontSize = 12.sp,
                            color = if (testsThisMonth >= tierCaps.maxTestsPerMonth!!) Color(0xFFC62828) else Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (showInstitute) {
                    item {
                        DashboardActionCard(
                            "Institute Hub",
                            Icons.Default.School,
                            Color(0xFF6A1B9A),
                            onOpenInstitute
                        )
                    }
                }
                item { DashboardActionCard("Create New Test",      Icons.Default.Add,      RedPrimary,        onCreateTest) }
                item { DashboardActionCard("Manage Tests",         Icons.Default.ListAlt,  Color(0xFF3F51B5), onManageTests) }
                item { DashboardActionCard("View Results",         Icons.Default.BarChart, Color(0xFF009688), onViewResults) }
                item { DashboardActionCard("Switch to Student Mode", Icons.Default.Person, Color(0xFFFF9800), onSwitchToStudent) }

                // Recent Tests
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("My Tests", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                when (val state = testsState) {
                    is UiState.Loading -> item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = RedPrimary) } }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item { Text("No tests yet. Tap 'Create New Test' above.", color = Color.Gray, fontSize = 13.sp) }
                        } else {
                            items(state.data.take(5)) { test ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(test.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("ID: ${test.testId}  •  ${test.sections.size} sections", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (test.isEnabled) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                        ) {
                                            Text(
                                                if (test.isEnabled) "Active" else "Off",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (test.isEnabled) Color(0xFF2E7D32) else Color(0xFFE65100)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is UiState.Error -> item { Text(state.message, color = Color.Red, fontSize = 13.sp) }
                    else -> {}
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onCreateTest,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = RedPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Test")
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardActionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
