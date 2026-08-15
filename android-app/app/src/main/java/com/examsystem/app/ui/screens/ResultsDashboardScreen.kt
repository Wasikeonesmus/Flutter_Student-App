package com.examsystem.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.data.SubscriptionCapabilities
import com.examsystem.app.data.models.Attempt
import com.examsystem.app.util.CertificatePdfExporter
import com.examsystem.app.ui.theme.*
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.ResultsViewModel
import com.examsystem.app.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsDashboardScreen(
    testId: String,
    testTitle: String = "Exam Results",
    totalMarks: Int = 100,
    passingMarks: Int = 50,
    sectionTitles: List<String> = emptyList(),
    vm: ResultsViewModel = viewModel(),
    instructorVm: InstructorViewModel = viewModel(),
    onBack: () -> Unit
) {
    val user by instructorVm.currentUser.collectAsState()
    val tierCaps = remember(user) {
        SubscriptionCapabilities.fromUser(user)
    }
    LaunchedEffect(testId) { vm.loadResults(testId) }
    val attemptsState by vm.attempts.collectAsState()
    val test by vm.currentTest.collectAsState()
    val actualTitle = test?.title ?: testTitle
    val actualTotal = test?.totalMarks ?: totalMarks
    val actualPassing = test?.passingMarks ?: passingMarks
    val actualSections = test?.sections?.map { it.title } ?: sectionTitles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black)))
    ) {
        // Header Row (takes natural height, no overlap)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column {
                Text("Results", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(actualTitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }

        // Bottom white card (takes remaining height, 0% overlap)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = Color.White
        ) {
            when (val state = attemptsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary)
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.message, color = Color.Red)
                }
                is UiState.Success -> ResultsContent(
                    testId = testId,
                    attempts = state.data,
                    testTitle = actualTitle,
                    totalMarks = actualTotal,
                    passingMarks = actualPassing,
                    sectionTitles = actualSections,
                    tierCaps = tierCaps,
                    initialLogoUrl = test?.resultsLogoUrl?.takeIf { it.isNotBlank() }
                        ?: user?.brandingLogoUrl.orEmpty(),
                    initialHeaderTitle = test?.resultsHeaderTitle?.takeIf { it.isNotBlank() }
                        ?: user?.brandingResultsTitle?.takeIf { it.isNotBlank() }.orEmpty(),
                    initialConductedBy = test?.resultsConductedBy?.takeIf { it.isNotBlank() }
                        ?: user?.brandingConductedBy?.takeIf { it.isNotBlank() }
                        ?: "Conducted by STUDENTS WELFARE FOUNDATION",
                    instructorVm = instructorVm,
                    modifier = Modifier.padding(top = 24.dp)
                )
                else -> {}
            }
        }
    }
}

@Composable
private fun ResultsContent(
    testId: String,
    attempts: List<Attempt>,
    testTitle: String,
    totalMarks: Int,
    passingMarks: Int,
    sectionTitles: List<String>,
    tierCaps: com.examsystem.app.data.TierCapabilities,
    initialLogoUrl: String,
    initialHeaderTitle: String,
    initialConductedBy: String,
    instructorVm: InstructorViewModel,
    modifier: Modifier = Modifier
) {
    val timesNewRoman = FontFamily.Serif // Closest available to Times New Roman on Android
    val context = androidx.compose.ui.platform.LocalContext.current
    var customTitleText by remember(testTitle, initialHeaderTitle) {
        mutableStateOf(initialHeaderTitle.ifBlank { testTitle })
    }
    var customConductedByText by remember(initialConductedBy) { mutableStateOf(initialConductedBy) }
    var logoUrl by remember(initialLogoUrl) { mutableStateOf(initialLogoUrl) }
    var showEditDialog by remember { mutableStateOf(false) }
    var upgradeHint by remember { mutableStateOf<String?>(null) }
    var brandingError by remember { mutableStateOf<String?>(null) }
    val showAdvanced = !tierCaps.basicAnalyticsOnly
    val brandingUploadState by instructorVm.brandingUploadState.collectAsState()

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            brandingError = null
            instructorVm.uploadBrandingLogo(context, uri)
        }
    }

    LaunchedEffect(initialLogoUrl, initialHeaderTitle, initialConductedBy) {
        logoUrl = initialLogoUrl
        if (initialHeaderTitle.isNotBlank()) customTitleText = initialHeaderTitle
        customConductedByText = initialConductedBy
    }

    LaunchedEffect(brandingUploadState) {
        when (val state = brandingUploadState) {
            is UiState.Success -> {
                logoUrl = state.data
                instructorVm.resetBrandingUploadState()
            }
            is UiState.Error -> {
                brandingError = state.message
                instructorVm.resetBrandingUploadState()
            }
            else -> {}
        }
    }

    if (showEditDialog && tierCaps.brandingUpload) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Branding & Titles", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Logo is saved in your account on Firestore (compressed; works on the free Spark plan — no Storage upgrade needed).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = "Your logo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { logoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = brandingUploadState !is UiState.Loading
                    ) {
                        if (brandingUploadState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Uploading…")
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (logoUrl.isBlank()) "Upload logo (PNG/JPEG)" else "Change logo")
                        }
                    }
                    brandingError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = customTitleText,
                        onValueChange = { customTitleText = it },
                        label = { Text("Main Title (Exam / Institution)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customConductedByText,
                        onValueChange = { customConductedByText = it },
                        label = { Text("Subtitle (Conducted By)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    instructorVm.saveResultsBranding(
                        testId = testId,
                        headerTitle = customTitleText,
                        conductedBy = customConductedByText,
                        logoUrl = logoUrl.takeIf { it.isNotBlank() }
                    )
                    showEditDialog = false
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Close") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header - matches RESULT PAGE FORMAT spec exactly
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = "Institution logo",
                        modifier = Modifier
                            .heightIn(max = 72.dp)
                            .fillMaxWidth(0.5f)
                            .padding(bottom = 8.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = tierCaps.brandingUpload) {
                            if (tierCaps.brandingUpload) showEditDialog = true
                            else upgradeHint = "Branding/logo on results requires Pro or Institute."
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = customTitleText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = timesNewRoman,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(8.dp))
                    if (tierCaps.brandingUpload) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Edit,
                            contentDescription = "Edit Title",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = customConductedByText,
                    fontSize = 13.sp,
                    fontFamily = timesNewRoman,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(enabled = tierCaps.brandingUpload) {
                            if (tierCaps.brandingUpload) showEditDialog = true
                            else upgradeHint = "Branding/logo on results requires Pro or Institute."
                        }
                        .padding(vertical = 4.dp)
                )
                if (!tierCaps.basicAnalyticsOnly && attempts.any { it.cheatAlerts > 0 }) {
                    Text(
                        "Anti-cheat alerts are shown below (Pro / Institute reports).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Marks: $totalMarks", fontWeight = FontWeight.Bold, fontFamily = timesNewRoman, fontSize = 14.sp)
                    Text("Passing Marks: $passingMarks", fontWeight = FontWeight.Bold, fontFamily = timesNewRoman, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Divider()
            }
        }

        // Table Header & Rows
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Column headers
                    Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                        TableCell("Rank", weight = 55.dp, isHeader = true)
                        TableCell("Name", weight = 140.dp, isHeader = true)
                        TableCell("Father Name", weight = 140.dp, isHeader = true)
                        TableCell("District", weight = 110.dp, isHeader = true)
                        TableCell("Gender", weight = 75.dp, isHeader = true)
                        sectionTitles.forEach { title ->
                            TableCell(title, weight = 90.dp, isHeader = true)
                        }
                        TableCell("Total", weight = 75.dp, isHeader = true)
                        if (showAdvanced) {
                            TableCell("Alerts", weight = 65.dp, isHeader = true)
                        }
                    }

                    // Rows ranked highest to lowest
                    attempts.forEachIndexed { index, attempt ->
                        val rowBg = if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        Row(modifier = Modifier.background(rowBg), verticalAlignment = Alignment.CenterVertically) {
                            TableCell("${index + 1}", weight = 55.dp)
                            TableCell(attempt.studentName, weight = 140.dp)
                            TableCell(attempt.fatherName, weight = 140.dp)
                            TableCell(attempt.district, weight = 110.dp)
                            TableCell(attempt.gender, weight = 75.dp)
                            sectionTitles.forEach { sTitle ->
                                val score = attempt.sectionScores[sTitle] ?: 0
                                TableCell("$score", weight = 90.dp)
                            }
                            TableCell("${attempt.totalScore}", weight = 75.dp, isTotal = true)
                            if (showAdvanced) {
                                TableCell(
                                    if (attempt.cheatAlerts > 0) "${attempt.cheatAlerts}" else "—",
                                    weight = 65.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Total Submissions: ${attempts.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                upgradeHint?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (tierCaps.brandingUpload) showEditDialog = true
                            else upgradeHint = "Branding/logo upload requires Pro or Institute."
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = tierCaps.brandingUpload
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Edit, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Branding")
                    }
                    Button(
                        onClick = {
                            if (tierCaps.studentReports) {
                                exportResultsToPdf(
                                    context,
                                    customTitleText,
                                    customConductedByText,
                                    totalMarks,
                                    passingMarks,
                                    sectionTitles,
                                    attempts
                                )
                            } else {
                                upgradeHint = "PDF student reports require Pro or Institute."
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = tierCaps.studentReports
                    ) {
                        Text("Download Results", fontWeight = FontWeight.Bold)
                    }
                }
                if (tierCaps.customCertificates) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            CertificatePdfExporter.exportPassCertificates(
                                context = context,
                                examTitle = customTitleText,
                                conductedBy = customConductedByText,
                                totalMarks = totalMarks,
                                passingMarks = passingMarks,
                                attempts = attempts
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Download pass certificates (PDF)", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "One certificate per student who scored at or above passing marks.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TableCell(text: String, weight: androidx.compose.ui.unit.Dp, isHeader: Boolean = false, isTotal: Boolean = false) {
    Box(
        modifier = Modifier
            .width(weight)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Serif,
            fontSize = if (isHeader) 12.sp else 13.sp,
            fontWeight = when {
                isHeader -> FontWeight.Bold
                isTotal -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

fun exportResultsToPdf(
    context: android.content.Context,
    testTitle: String,
    conductedBy: String,
    totalMarks: Int,
    passingMarks: Int,
    sectionTitles: List<String>,
    attempts: List<Attempt>
) {
    try {
        val document = android.graphics.pdf.PdfDocument()
        
        val baseWidth = 1200
        val colWidths = mutableListOf<Float>()
        colWidths.add(80f)  // Rank
        colWidths.add(220f) // Name
        colWidths.add(220f) // Father Name
        colWidths.add(160f) // District
        colWidths.add(100f) // Gender
        sectionTitles.forEach { _ -> colWidths.add(120f) }
        colWidths.add(100f) // Total
        
        val totalTableWidth = colWidths.sum()
        val pageWidth = maxOf(baseWidth, (totalTableWidth + 100).toInt())
        val pageHeight = maxOf(1600, 400 + (attempts.size + 2) * 60)
        
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 28f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        // Title
        canvas.drawText(testTitle, pageWidth / 2f, 80f, textPaint)
        
        // Conducted By
        textPaint.apply {
            textSize = 20f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            color = android.graphics.Color.DKGRAY
        }
        canvas.drawText(conductedBy, pageWidth / 2f, 120f, textPaint)
        
        // Marks Info
        textPaint.apply {
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.LEFT
            color = android.graphics.Color.BLACK
        }
        canvas.drawText("Total Marks: $totalMarks", 50f, 180f, textPaint)
        
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText("Passing Marks: $passingMarks", pageWidth - 50f, 180f, textPaint)
        
        // Divider line
        val linePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            strokeWidth = 2f
        }
        canvas.drawLine(50f, 200f, pageWidth - 50f, 200f, linePaint)
        
        // Table styling
        val headerBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E8DEF8") // primaryContainer approx
            style = android.graphics.Paint.Style.FILL
        }
        val rowBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F4EEFC")
            style = android.graphics.Paint.Style.FILL
        }
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#CAC4D0")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val cellTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        var currentY = 240f
        val rowHeight = 50f
        var currentX = 50f
        
        val headers = mutableListOf("Rank", "Name", "Father Name", "District", "Gender")
        headers.addAll(sectionTitles)
        headers.add("Total")
        
        // Draw Header Row
        headers.forEachIndexed { index, header ->
            val width = colWidths[index]
            canvas.drawRect(currentX, currentY, currentX + width, currentY + rowHeight, headerBgPaint)
            canvas.drawRect(currentX, currentY, currentX + width, currentY + rowHeight, borderPaint)
            
            // Text vertical centering
            val textY = currentY + (rowHeight / 2f) - ((cellTextPaint.descent() + cellTextPaint.ascent()) / 2f)
            canvas.drawText(header, currentX + (width / 2f), textY, cellTextPaint)
            currentX += width
        }
        currentY += rowHeight
        
        // Draw Data Rows
        cellTextPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        attempts.forEachIndexed { rowIndex, attempt ->
            currentX = 50f
            val isEven = rowIndex % 2 == 0
            
            val rowData = mutableListOf(
                "${rowIndex + 1}",
                attempt.studentName,
                attempt.fatherName,
                attempt.district,
                attempt.gender
            )
            sectionTitles.forEach { sTitle ->
                rowData.add("${attempt.sectionScores[sTitle] ?: 0}")
            }
            rowData.add("${attempt.totalScore}")
            
            rowData.forEachIndexed { colIndex, text ->
                val width = colWidths[colIndex]
                if (!isEven) {
                    canvas.drawRect(currentX, currentY, currentX + width, currentY + rowHeight, rowBgPaint)
                }
                canvas.drawRect(currentX, currentY, currentX + width, currentY + rowHeight, borderPaint)
                
                cellTextPaint.typeface = if (colIndex == rowData.size - 1) {
                    android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
                } else {
                    android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
                }
                
                val textY = currentY + (rowHeight / 2f) - ((cellTextPaint.descent() + cellTextPaint.ascent()) / 2f)
                canvas.drawText(text, currentX + (width / 2f), textY, cellTextPaint)
                currentX += width
            }
            currentY += rowHeight
        }
        
        document.finishPage(page)
        
        val fileName = "Exam_Results_${System.currentTimeMillis()}.pdf"
        var fileSaved = false
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    document.writeTo(outputStream)
                    fileSaved = true
                }
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
                fileSaved = true
            }
        }
        document.close()
        
        if (fileSaved) {
            android.widget.Toast.makeText(context, "Results downloaded successfully to Downloads folder!", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(context, "Failed to save PDF.", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Error exporting PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
