package com.examsystem.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examsystem.app.ui.theme.*
import com.examsystem.app.util.PlatformPricing
import com.examsystem.app.viewmodel.StudentViewModel
import com.examsystem.app.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPaymentScreen(
    vm: StudentViewModel,
    onBack: () -> Unit
) {
    var referenceNumber by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var receiptMimeType by remember { mutableStateOf("") }
    var receiptFileName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val platformSettingsState by vm.platformSettings.collectAsState()
    val paymentState by vm.paymentState.collectAsState()
    val paymentApproved by vm.paymentApproved.collectAsState()
    val currentAttempt by vm.currentAttempt.collectAsState()

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

    LaunchedEffect(Unit) {
        vm.loadPlatformSettings()
        vm.ensurePaymentWatch()
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.stopPaymentApprovalPolling()
            vm.refreshResultsAfterPayment()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Unlock Full Results", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
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
                text = "Detailed Answers",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            val priceHint = when (val ps = platformSettingsState) {
                is UiState.Success -> "Pay ${PlatformPricing.studentResultPriceLabel(ps.data)} to view full test details"
                else -> "Pay the fee to view full test details"
            }
            Text(
                text = priceHint,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.65f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (val state = platformSettingsState) {
                    is UiState.Loading -> item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = RedPrimary) } }
                    is UiState.Error   -> item { Text(state.message, color = Color.Red, fontSize = 13.sp) }
                    is UiState.Success -> {
                        val accounts = state.data["accounts"] as? List<Map<String, Any>> ?: emptyList()
                        val priceLabel = PlatformPricing.studentResultPriceLabel(state.data)

                        item {
                            Text("1. Send Payment To", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Please send $priceLabel to any of the accounts below.", fontSize = 13.sp, color = Color.Gray)
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

                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("2. Upload Receipt", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

                            if (paymentApproved) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "✓ Payment approved! Full results are unlocked.",
                                            color = Color(0xFF2E7D32),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        currentAttempt?.let {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "Score: ${it.totalScore}",
                                                fontSize = 14.sp,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                                ) {
                                    Text("VIEW UNLOCKED RESULTS", fontWeight = FontWeight.Bold)
                                }
                            } else if (paymentState is UiState.Success) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "✓ Payment submitted. Waiting for admin approval…",
                                            color = Color(0xFFF57F17),
                                            fontSize = 14.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "This screen updates automatically when approved.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { vm.refreshResultsAfterPayment() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("CHECK APPROVAL STATUS")
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(25.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                                ) {
                                    Text("GO BACK")
                                }
                            } else {
                                Button(
                                    onClick = { vm.submitPayment(context, receiptUri!!, referenceNumber, receiptMimeType) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                    enabled = receiptUri != null && referenceNumber.isNotBlank() && paymentState !is UiState.Loading
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
