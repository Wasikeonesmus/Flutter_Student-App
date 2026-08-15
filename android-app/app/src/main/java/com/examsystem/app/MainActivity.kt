package com.examsystem.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.examsystem.app.ui.screens.*
import com.examsystem.app.util.AntiCheatConfig
import com.examsystem.app.util.AntiCheatViolation
import com.examsystem.app.util.ExamQuestionBuilder
import com.examsystem.app.viewmodel.InstituteViewModel
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.ResultsViewModel
import com.examsystem.app.viewmodel.StudentViewModel
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.examsystem.app.ads.RewardedAdManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob on a background thread to avoid delaying the first frame
        Thread {
            MobileAds.initialize(this@MainActivity) {}
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .build()
            )
        }.start()
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    val instructorVm: InstructorViewModel = viewModel()
                    val instituteVm: InstituteViewModel = viewModel()
                    val studentVm: StudentViewModel = viewModel()
                    val resultsVm: ResultsViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "splash") {

                        composable("splash") {
                            SplashScreen(onFinish = {
                                navController.navigate("role_selection") { popUpTo("splash") { inclusive = true } }
                            })
                        }

                        composable("role_selection") {
                            RoleSelectionScreen(
                                onInstructorSelected = { navController.navigate("instructor_login") },
                                onStudentSelected = { navController.navigate("student_test_id") },
                                onTestResultsSelected = { navController.navigate("student_attempts") }
                            )
                        }

                        composable("student_attempts") {
                            StudentAttemptsScreen(
                                vm = studentVm,
                                onNavigateToResults = { navController.navigate("submit_success") },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // ── INSTRUCTOR FLOW ───────────────────────────────────
                        composable("instructor_login") {
                            InstructorLoginScreen(
                                vm = instructorVm,
                                onLoginSuccess = { user ->
                                    if (user.isApproved && user.hasActiveSubscription) {
                                        navController.navigate("instructor_dashboard") { popUpTo("role_selection") }
                                    } else {
                                        navController.navigate("instructor_subscription") { popUpTo("role_selection") }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("instructor_subscription") {
                            InstructorSubscriptionScreen(
                                vm = instructorVm,
                                onActivated = { navController.navigate("instructor_dashboard") { popUpTo("role_selection") } },
                                onLogout = { navController.navigate("role_selection") { popUpTo(0) { inclusive = true } } }
                            )
                        }

                        composable("instructor_dashboard") {
                            InstructorDashboardScreen(
                                vm = instructorVm,
                                onCreateTest = { navController.navigate("create_test") },
                                onManageTests = { navController.navigate("manage_tests") },
                                onViewResults = { navController.navigate("manage_tests") },
                                onSwitchToStudent = { navController.navigate("student_test_id") },
                                onOpenInstitute = { navController.navigate("institute_dashboard") },
                                onLogout = { navController.navigate("role_selection") { popUpTo(0) { inclusive = true } } }
                            )
                        }

                        composable("institute_dashboard") {
                            InstituteDashboardScreen(
                                instructorVm = instructorVm,
                                instituteVm = instituteVm,
                                onBack = { navController.popBackStack() },
                                onMembers = { navController.navigate("institute_members") },
                                onBatches = { navController.navigate("institute_batches") }
                            )
                        }

                        composable("institute_members") {
                            InstituteMembersScreen(
                                instructorVm = instructorVm,
                                instituteVm = instituteVm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("institute_batches") {
                            InstituteBatchesScreen(
                                instituteVm = instituteVm,
                                onBack = { navController.popBackStack() },
                                onOpenBatch = { instituteId, batchId ->
                                    navController.navigate("institute_batch/$instituteId/$batchId")
                                }
                            )
                        }

                        composable(
                            route = "institute_batch/{instituteId}/{batchId}",
                            arguments = listOf(
                                navArgument("instituteId") { type = NavType.StringType },
                                navArgument("batchId") { type = NavType.StringType }
                            )
                        ) { backStack ->
                            val instituteId = backStack.arguments?.getString("instituteId") ?: ""
                            val batchId = backStack.arguments?.getString("batchId") ?: ""
                            BatchDetailScreen(
                                instituteId = instituteId,
                                batchId = batchId,
                                instructorVm = instructorVm,
                                instituteVm = instituteVm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("create_test") {
                            WebCreateTestScreen(
                                onTestCreated = { navController.navigate("manage_tests") { popUpTo("instructor_dashboard") } },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("manage_tests") {
                            TestManagementScreen(
                                vm = instructorVm,
                                onViewResults = { testId -> navController.navigate("results/$testId") },
                                onEditTest = { testId -> navController.navigate("edit_test/$testId") },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "edit_test/{testId}",
                            arguments = listOf(navArgument("testId") { type = NavType.StringType })
                        ) { backStack ->
                            val testId = backStack.arguments?.getString("testId") ?: ""
                            WebCreateTestScreen(
                                editTestId = testId,
                                onTestCreated = { navController.navigate("manage_tests") { popUpTo("instructor_dashboard") } },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "results/{testId}",
                            arguments = listOf(navArgument("testId") { type = NavType.StringType })
                        ) { backStack ->
                            val testId = backStack.arguments?.getString("testId") ?: ""
                            ResultsDashboardScreen(
                                testId = testId,
                                vm = resultsVm,
                                instructorVm = instructorVm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // ── STUDENT FLOW ──────────────────────────────────────
                        composable("student_test_id") {
                            val scope = rememberCoroutineScope()
                            StudentTestIdScreen(
                                vm = studentVm,
                                onTestFound = { testId ->
                                    studentVm.loadSavedProfile()
                                    val test = studentVm.currentTest
                                    scope.launch {
                                        when {
                                            test != null && studentVm.canSkipStudentForm(test) -> {
                                                studentVm.ensureRegisteredOnBatch(test)
                                                navController.navigate("instructions/$testId") {
                                                    popUpTo("role_selection") { inclusive = false }
                                                }
                                            }
                                            test != null && test.roster.isNotEmpty() -> {
                                                navController.navigate("student_pick/$testId")
                                            }
                                            else -> navController.navigate("student_form/$testId")
                                        }
                                    }
                                },
                                onAlreadySubmitted = {
                                    navController.navigate("submit_success") { popUpTo("role_selection") { inclusive = false } }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "student_pick/{testId}",
                            arguments = listOf(navArgument("testId") { type = NavType.StringType })
                        ) { backStack ->
                            val testId = backStack.arguments?.getString("testId") ?: ""
                            val test = studentVm.currentTest
                            val roster = test?.roster.orEmpty()
                            val scope = rememberCoroutineScope()
                            if (roster.isEmpty()) {
                                LaunchedEffect(Unit) {
                                    navController.navigate("student_form/$testId") {
                                        popUpTo("student_test_id")
                                    }
                                }
                            } else {
                                StudentPickScreen(
                                    vm = studentVm,
                                    testId = testId,
                                    roster = roster,
                                    onContinue = {
                                        scope.launch {
                                            test?.let { studentVm.ensureRegisteredOnBatch(it) }
                                            navController.navigate("instructions/$testId") {
                                                popUpTo("role_selection") { inclusive = false }
                                            }
                                        }
                                    },
                                    onNewStudent = {
                                        navController.navigate("student_form/$testId")
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable(
                            route = "student_form/{testId}",
                            arguments = listOf(navArgument("testId") { type = NavType.StringType })
                        ) { backStack ->
                            val testId = backStack.arguments?.getString("testId") ?: ""
                            val test = studentVm.currentTest
                            val scope = rememberCoroutineScope()
                            StudentFormScreen(
                                vm = studentVm,
                                testId = testId,
                                classLinked = test?.instituteId?.isNotBlank() == true && test.batchId.isNotBlank(),
                                onContinue = { name, father, dist, gen, roll ->
                                    studentVm.studentName = name
                                    studentVm.fatherName = father
                                    studentVm.district = dist
                                    studentVm.gender = gen
                                    studentVm.studentRoll = roll
                                    studentVm.saveProfile()
                                    scope.launch {
                                        test?.let { studentVm.ensureRegisteredOnBatch(it) }
                                        navController.navigate("instructions/$testId") {
                                            popUpTo("role_selection") { inclusive = false }
                                        }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "instructions/{testId}",
                            arguments = listOf(navArgument("testId") { type = NavType.StringType })
                        ) { backStack ->
                            val testId = backStack.arguments?.getString("testId") ?: ""
                            val test = studentVm.currentTest
                            val antiCheat = remember(test) { AntiCheatConfig.fromTest(test) }
                            InstructionsScreen(
                                testTitle = test?.title ?: "Exam",
                                totalQuestions = test?.sections?.sumOf { it.questions.orEmpty().size } ?: 0,
                                timeDurationMins = test?.durationMinutes ?: 60,
                                antiCheat = antiCheat,
                                onStartExam = {
                                    if (testId.isNotBlank()) studentVm.beginExamSession(testId)
                                    navController.navigate("exam") { popUpTo("role_selection") { inclusive = false } }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("exam") {
                            val test = studentVm.currentTest
                            val testId = test?.testId ?: ""
                            val cheatAlertCount by studentVm.cheatAlertCountFlow.collectAsState()

                            // PERSISTENCE: Load previous progress if available
                            val initialAnswers = remember(testId) { studentVm.loadProgress(testId) }

                            val shuffleSeed = remember(testId) { 
                                if (testId.isNotBlank()) studentVm.getOrCreateShuffleSeed(testId) else 0L
                            }
                            val allQuestions = remember(test, shuffleSeed) { 
                                test?.let { ExamQuestionBuilder.build(it, shuffleSeed) } ?: emptyList()
                            }
                            val antiCheat = remember(test) { AntiCheatConfig.fromTest(test) }

                            if (allQuestions.isEmpty()) {
                                LaunchedEffect(Unit) {
                                    Toast.makeText(context, "No questions found.", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                }
                                Box(modifier = Modifier.fillMaxSize())
                            } else {
                                ExamScreen(
                                    testTitle = test?.title ?: "Exam",
                                    questions = allQuestions,
                                    timeDurationMins = test?.durationMinutes ?: 60,
                                    antiCheat = antiCheat,
                                    cheatAlertCount = cheatAlertCount,
                                    initialAnswers = initialAnswers,
                                    onProgressUpdate = { updatedAnswers ->
                                        studentVm.saveProgress(testId, updatedAnswers)
                                    },
                                    onCheatDetected = { violation ->
                                        studentVm.recordCheatViolation(violation)
                                        Toast.makeText(
                                            context,
                                            "Anti-cheat: ${violation.label}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onSubmit = { finalAnswers ->
                                        studentVm.submitExam(finalAnswers)
                                        val test = studentVm.currentTest
                                        val needsAd = test?.releaseScoreMode != "full_answers"
                                        if (needsAd) {
                                            RewardedAdManager.preload(context)
                                        }
                                        navController.navigate("submit_success") { popUpTo(0) { inclusive = true } }
                                    }
                                )
                            }
                        }

                        composable("submit_success") {
                            SubmitSuccessScreen(
                                vm = studentVm,
                                onDone = { navController.navigate("role_selection") { popUpTo(0) { inclusive = true } } },
                                onUnlockDetails = { navController.navigate("student_payment") }
                            )
                        }

                        composable("student_payment") {
                            StudentPaymentScreen(
                                vm = studentVm,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
