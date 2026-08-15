package com.examsystem.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Payment
import com.examsystem.app.data.SubscriptionPlans
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examsystem.app.ui.theme.*
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorSubscriptionScreen(
    vm: InstructorViewModel,
    onActivated: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf("basic") }
    var referenceNumber by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var receiptMimeType by remember { mutableStateOf("") }
    var receiptFileName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val platformSettingsState by vm.platformSettings.collectAsState()
    val paymentState by vm.paymentState.collectAsState()
    val user by vm.currentUser.collectAsState()

    // OpenDocument supports multiple MIME types (images + PDFs)
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        receiptUri = uri
        if (uri != null) {
            receiptMimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            receiptFileName = when {
                receiptMimeType.contains("pdf") -> "PDF selected ✓"
                else -> "Image selected ✓"
            }
        }
    }

    LaunchedEffect(Unit) { vm.loadPlatformSettings() }

    LaunchedEffect(user) {
        if (user?.isApproved == true && user?.hasActiveSubscription == true) {
            onActivated()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        // Top bar logout
        IconButton(
            onClick = { vm.logout(); onLogout() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
        }

        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Activate Account",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Welcome, ${user?.name ?: "Instructor"}!",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )
        }

        // Bottom white card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.72f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Instructor subscription plans",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose Basic, Pro, or Institute — pay and upload your receipt. Admin approves payments in the web dashboard.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                when (val state = platformSettingsState) {
                    is UiState.Loading -> item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = RedPrimary) } }
                    is UiState.Error   -> item { Text(state.message, color = Color.Red, fontSize = 13.sp) }
                    is UiState.Success -> {
                        val tiers = SubscriptionPlans.resolveTiers(state.data)
                        val accounts = state.data["accounts"] as? List<Map<String, Any>> ?: emptyList()

                        item {
                            Text("1. Instructor subscription plan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        tiers.forEach { tierMap ->
                            val key = tierMap["key"] as? String ?: ""
                            val label = tierMap["label"] as? String ?: key
                            val subtitle = tierMap["subtitle"] as? String ?: ""
                            val price = tierMap["price"]?.toString() ?: "0"
                            val contactOnly = when (val c = tierMap["contactOnly"]) {
                                is Boolean -> c
                                else -> false
                            }
                            val features = (tierMap["features"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            item {
                                val selected = selectedPlan == key
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPlan = key },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) RedPrimary.copy(alpha = 0.1f) else Color(0xFFF5F5F5)
                                    ),
                                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, RedPrimary) else null
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    label,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    color = if (selected) RedPrimary else Color.Black
                                                )
                                                if (subtitle.isNotBlank()) {
                                                    Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                                                }
                                            }
                                            Text(
                                                text = if (contactOnly) "Contact us" else "$$price/mo",
                                                color = RedPrimary,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = if (contactOnly) 14.sp else 16.sp
                                            )
                                        }
                                        if (features.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            features.forEach { feature ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = RedPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(feature, fontSize = 13.sp, color = Color.DarkGray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Payment accounts
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("2. Send Payment To", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        accounts.forEach { accMap ->
                            val method = accMap["method"] as? String ?: ""
                            val number = accMap["number"] as? String ?: ""
                            val type   = accMap["type"]   as? String ?: ""
                            val name   = accMap["name"]   as? String ?: ""
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(method, fontWeight = FontWeight.Bold, color = RedPrimary, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Number: $number", fontSize = 13.sp)
                                        Text("Type: $type",     fontSize = 13.sp)
                                        Text("Holder: $name",   fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // Upload section
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("3. Upload Receipt", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = referenceNumber,
                                onValueChange = { referenceNumber = it },
                                label = { Text("Transaction / Reference Number") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedBorderColor = RedPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4A6A))
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (receiptUri == null) "Select Image or PDF" else receiptFileName)
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (paymentState is UiState.Error) {
                                Text((paymentState as UiState.Error).message, color = Color.Red, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (paymentState is UiState.Success) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "✓ Payment submitted! Please wait for the admin to approve your account.",
                                        modifier = Modifier.padding(16.dp),
                                        color = Color(0xFF2E7D32),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                val selectedContactOnly = tiers.any { tierMap ->
                                    (tierMap["key"] as? String) == selectedPlan &&
                                        when (val c = tierMap["contactOnly"]) {
                                            is Boolean -> c
                                            else -> false
                                        }
                                }
                                if (selectedContactOnly) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            "Institute Plan is for academies and schools. Contact the platform admin to activate — do not submit payment here.",
                                            modifier = Modifier.padding(16.dp),
                                            color = Color(0xFF1565C0),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Button(
                                    onClick = { vm.submitPayment(context, receiptUri!!, selectedPlan, referenceNumber, receiptMimeType) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                    enabled = !selectedContactOnly &&
                                        receiptUri != null &&
                                        referenceNumber.isNotBlank() &&
                                        paymentState !is UiState.Loading
                                ) {
                                    if (paymentState is UiState.Loading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text("SUBMIT PAYMENT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
