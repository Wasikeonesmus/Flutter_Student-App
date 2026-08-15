package com.examsystem.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examsystem.app.ui.theme.*
import com.examsystem.app.ui.components.SmartMathText
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.delay
import com.examsystem.app.util.ResultsRelease
import com.examsystem.app.viewmodel.SecureResultState
import com.examsystem.app.util.PlatformPricing
import com.examsystem.app.viewmodel.StudentViewModel
import com.examsystem.app.viewmodel.UiState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.examsystem.app.ads.RewardedAdManager
import com.examsystem.app.util.findActivity

@Composable
fun SubmitSuccessScreen(vm: StudentViewModel = viewModel(), onDone: () -> Unit, onUnlockDetails: () -> Unit = {}) {
    val scale = remember { Animatable(0f) }
    val submitState by vm.submitState.collectAsState()
    val attemptState by vm.currentAttempt.collectAsState()
    val attempt = attemptState
    val test = vm.currentTest
    var showDetails by remember { mutableStateOf(false) }
    
    val secureResultState by vm.secureResultState.collectAsState()
    val platformSettingsState by vm.platformSettings.collectAsState()
    var isWatchingAd by remember { mutableStateOf(false) }
    var adLoading by remember { mutableStateOf(false) }
    var adErrorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    fun unlockScoreAfterAd() {
        if (test != null && attempt != null) {
            vm.loadResultsSecurely(test.testId, attempt.attemptId, adWatched = true)
        }
    }

    fun showRewardedAd() {
        val activity = context.findActivity()
        if (activity == null) {
            adErrorMessage = "Cannot open ad on this screen. Restart the app and try again."
            Toast.makeText(context, adErrorMessage, Toast.LENGTH_LONG).show()
            return
        }
        adErrorMessage = null
        adLoading = true

        fun tryShowLoadedAd() {
            if (!RewardedAdManager.isReady()) return
            adLoading = false
            isWatchingAd = true
            RewardedAdManager.show(
                activity = activity,
                onRewardEarned = {
                    isWatchingAd = false
                    unlockScoreAfterAd()
                    Toast.makeText(context, "Score unlocked", Toast.LENGTH_SHORT).show()
                },
                onFailed = { msg ->
                    isWatchingAd = false
                    adErrorMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
                onDismissed = { isWatchingAd = false }
            )
        }

        if (RewardedAdManager.isReady()) {
            tryShowLoadedAd()
            return
        }

        RewardedAdManager.preload(
            context = context,
            onReady = { tryShowLoadedAd() },
            onFailed = { msg ->
                adLoading = false
                adErrorMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    LaunchedEffect(Unit) {
        vm.loadPlatformSettings()
        RewardedAdManager.preload(context)
    }

    LaunchedEffect(attempt?.attemptId, test?.testId, attempt?.totalScore, attempt?.rank, attempt?.hasPaidForDetails) {
        if (test != null && attempt != null) {
            vm.loadResultsSecurely(
                test.testId,
                attempt.attemptId,
                adWatched = attempt.hasPaidForDetails
            )
        }
    }

    var countdownText by remember(test?.resultReleaseTime, test?.resultsReleasedEarly) {
        mutableStateOf(test?.let { ResultsRelease.countdownLabel(it) })
    }
    LaunchedEffect(test?.testId, test?.resultReleaseTime, test?.resultsReleasedEarly) {
        val t = test ?: return@LaunchedEffect
        while (true) {
            countdownText = ResultsRelease.countdownLabel(t)
            kotlinx.coroutines.delay(60_000)
        }
    }

    // While results are time-locked, poll every 30s so admin early-release is picked up
    LaunchedEffect(test?.testId, attempt?.attemptId, secureResultState) {
        val t = test ?: return@LaunchedEffect
        val att = attempt ?: return@LaunchedEffect
        if (secureResultState !is SecureResultState.NotYetReleased) return@LaunchedEffect
        while (vm.secureResultState.value is SecureResultState.NotYetReleased) {
            kotlinx.coroutines.delay(30_000)
            if (vm.secureResultState.value !is SecureResultState.NotYetReleased) break
            vm.refreshTestAndResults(t.testId, att.attemptId, adWatched = att.hasPaidForDetails)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.refreshResultsAfterPayment()
        }
    }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    // Loading State (Initial Submission)
    if (submitState is UiState.Loading && attempt == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = RedPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Submitting your exam, please wait...", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Do not exit the app.", color = Color.Gray, fontSize = 14.sp)
            }
        }
        return
    }

    // Error State (Initial Submission)
    if (submitState is UiState.Error && attempt == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Error, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Submission Failed", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text((submitState as UiState.Error).message, color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)) {
                    Text("GO BACK", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Ad loading overlay
    if (adLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Loading Sponsored Ad...", color = Color.White)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedPrimary, Color.Black))),
        contentAlignment = Alignment.Center
    ) {
        // Bottom white card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.65f),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                item {
                    val headerText = when (val state = secureResultState) {
                        is com.examsystem.app.viewmodel.SecureResultState.Summary -> "Your Score: ${state.totalScore} / ${state.totalMarks}"
                        is com.examsystem.app.viewmodel.SecureResultState.Full -> "Your Score: ${state.totalScore} / ${state.totalMarks}"
                        is com.examsystem.app.viewmodel.SecureResultState.NotYetReleased -> "Results Locked"
                        is com.examsystem.app.viewmodel.SecureResultState.AdRequired -> "Score & Details Locked"
                        is com.examsystem.app.viewmodel.SecureResultState.Loading -> "Fetching Results..."
                        else -> "Exam Submitted!"
                    }
                    Text(
                        text = headerText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    val subtitleText = when (val state = secureResultState) {
                        is com.examsystem.app.viewmodel.SecureResultState.Summary -> "Percentage: ${state.percentage}% • Status: ${if (state.passed) "PASSED" else "FAILED"}"
                        is com.examsystem.app.viewmodel.SecureResultState.Full -> "Percentage: ${state.percentage}% • Status: ${if (state.passed) "PASSED" else "FAILED"}"
                        is com.examsystem.app.viewmodel.SecureResultState.NotYetReleased -> "Official release time has not been reached."
                        is com.examsystem.app.viewmodel.SecureResultState.AdRequired -> "Sponsored ad or payment required to view your score."
                        else -> "Your answers have been submitted successfully.\nResults have been recorded."
                    }
                    Text(
                        text = subtitleText,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }

                // Render content depending on results fetching status
                when (val state = secureResultState) {
                    is com.examsystem.app.viewmodel.SecureResultState.Loading -> {
                        item {
                            Spacer(Modifier.height(32.dp))
                            CircularProgressIndicator(color = RedPrimary)
                        }
                    }
                    is com.examsystem.app.viewmodel.SecureResultState.Error -> {
                        item {
                            Spacer(Modifier.height(32.dp))
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            if (attempt != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Your submission is saved. Score: ${attempt.totalScore} (rank #${attempt.rank}).",
                                    fontSize = 14.sp,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    is com.examsystem.app.viewmodel.SecureResultState.NotYetReleased -> {
                        item {
                            Spacer(Modifier.height(24.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = RedPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Results not published yet",
                                        fontSize = 18.sp,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    test?.let { t ->
                                        ResultsRelease.formattedReleaseTime(t)?.let { whenStr ->
                                            Text(
                                                text = "Scheduled release:\n$whenStr",
                                                fontSize = 15.sp,
                                                color = RedPrimary,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                    countdownText?.let { cd ->
                                        Text(
                                            text = cd,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1565C0),
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    Text(
                                        text = state.message,
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "When results are released, this screen will update automatically. You can also tap the button below to check now.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    if (test != null && attempt != null) {
                                        OutlinedButton(
                                            onClick = {
                                                vm.refreshTestAndResults(
                                                    test.testId,
                                                    attempt.attemptId,
                                                    adWatched = attempt.hasPaidForDetails
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("CHECK IF RESULTS ARE READY", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is com.examsystem.app.viewmodel.SecureResultState.AdRequired -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Results are now available! Watch the ad below to see your score.",
                                        fontSize = 14.sp,
                                        color = Color(0xFF1B5E20),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = RedPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Score & Details Locked",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.Black
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Watch a brief sponsored video to unlock and check your test score immediately.",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    adErrorMessage?.let { err ->
                                        Text(
                                            err,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { showRewardedAd() },
                                        enabled = !adLoading && !isWatchingAd,
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                                    ) {
                                        if (adLoading) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("WATCH AD TO UNLOCK SCORE", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (adErrorMessage != null) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = { showRewardedAd() }) {
                                            Text("Retry loading ad")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is com.examsystem.app.viewmodel.SecureResultState.Summary -> {
                        item {
                            Spacer(Modifier.height(24.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Your score is unlocked",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.Black
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Only your total score is shown here. Other students' results are hidden. Pay the fee to unlock section-wise scores, correct answers, and the leaderboard.",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = onUnlockDetails,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4A6A))
                                    ) {
                                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        val unlockPrice = when (val ps = platformSettingsState) {
                                            is UiState.Success -> PlatformPricing.studentResultPriceLabel(ps.data).uppercase()
                                            else -> "FEE"
                                        }
                                        Text("VIEW DETAILED ANSWERS ($unlockPrice)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    is com.examsystem.app.viewmodel.SecureResultState.Full -> {
                        item {
                            Spacer(Modifier.height(24.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Section-wise Scores", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                    Divider(color = Color.LightGray)
                                    state.sectionScores.forEach { (secIdOrTitle, score) ->
                                        val secTitle = test?.sections?.find { it.id == secIdOrTitle || it.title == secIdOrTitle }?.title 
                                            ?: secIdOrTitle
                                        val secTotal = test?.sections?.find { it.id == secIdOrTitle || it.title == secIdOrTitle }?.questions?.sumOf { it.marks }
                                            ?: 0
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(secTitle.ifEmpty { "Section" }, fontSize = 14.sp, color = Color.DarkGray)
                                            Text("$score / $secTotal", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.leaderboard.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(24.dp))
                                Text("Leaderboard", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Rk", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.15f), color = Color.Black)
                                            Text("Name", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), color = Color.Black)
                                            Text("Score", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f), textAlign = TextAlign.End, color = Color.Black)
                                        }
                                        Divider(color = Color.LightGray)
                                        state.leaderboard.forEachIndexed { index, att ->
                                            val isMe = att.isMe
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${att.rank}", fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(0.15f), color = if (isMe) RedPrimary else Color.Black)
                                                Column(modifier = Modifier.weight(0.5f)) {
                                                    Text(att.studentName, fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium, color = if (isMe) RedPrimary else Color.Black, fontSize = 14.sp)
                                                    Text("${att.fatherName} • ${att.district}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Text("${att.totalScore}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f), textAlign = TextAlign.End, color = if (isMe) RedPrimary else Color.Black)
                                            }
                                            if (index < state.leaderboard.size - 1) Divider(color = Color.LightGray.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { showDetails = !showDetails },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
                            ) {
                                Text(if (showDetails) "HIDE DETAILED ANSWERS" else "VIEW DETAILED ANSWERS", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showDetails) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                val fullTest = test?.copy(sections = state.sections) ?: test
                                val fullAttempt = attempt?.copy(answers = state.answers, sectionScores = state.sectionScores) ?: attempt
                                if (fullAttempt != null && fullTest != null) {
                                    com.examsystem.app.ui.components.DetailedAnswersHtmlView(
                                        attempt = fullAttempt,
                                        test = fullTest,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 600.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }

                item {
                    Spacer(Modifier.height(36.dp))
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("BACK TO HOME", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Animated checkmark centered on the gradient part (above card)
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(110.dp)
                .scale(scale.value)
                .align(Alignment.Center)
                .offset(y = (-140).dp)
        )
    }
}
