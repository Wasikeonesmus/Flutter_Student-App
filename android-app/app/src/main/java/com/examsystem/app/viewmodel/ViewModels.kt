package com.examsystem.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examsystem.app.data.models.*
import com.examsystem.app.data.repository.FirebaseRepository
import com.examsystem.app.util.ExamSchedule
import com.examsystem.app.util.LatexRender
import com.examsystem.app.util.ResultsRelease
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

// ─── Shared UI State ─────────────────────────────────────────────────────────
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

// ─── Secure Result State (for student result access control) ─────────────────────
sealed class SecureResultState {
    /** Results haven't been fetched yet. */
    object Idle : SecureResultState()
    object Loading : SecureResultState()
    /** Server says ad must be watched before any data is released. */
    object AdRequired : SecureResultState()
    /** Result release time has not been reached — message includes the scheduled time. */
    data class NotYetReleased(val message: String) : SecureResultState()
    /** Ad watched — only total score / percentage / pass-fail revealed. */
    data class Summary(
        val totalScore: Int,
        val totalMarks: Int,
        val percentage: Int,
        val passed: Boolean,
        val rank: Int,
        val studentName: String
    ) : SecureResultState()
    /** Full paid result: section scores, answers, leaderboard. */
    data class Full(
        val totalScore: Int,
        val totalMarks: Int,
        val percentage: Int,
        val passed: Boolean,
        val rank: Int,
        val studentName: String,
        val sectionScores: Map<String, Int>,
        val answers: Map<String, String>,
        val sections: List<com.examsystem.app.data.models.Section>,
        val leaderboard: List<LeaderboardEntry>
    ) : SecureResultState()
    data class Error(val message: String) : SecureResultState()
}

data class LeaderboardEntry(
    val rank: Int,
    val studentName: String,
    val fatherName: String,
    val district: String,
    val gender: String,
    val totalScore: Int,
    val sectionScores: Map<String, Int>,
    val isMe: Boolean
)

// ─── Instructor ViewModel ─────────────────────────────────────────────────────
class InstructorViewModel : ViewModel() {
    private val repo = FirebaseRepository()
    private val _loginState = MutableStateFlow<UiState<User>>(UiState.Idle)
    val loginState: StateFlow<UiState<User>> = _loginState
    private val _tests = MutableStateFlow<UiState<List<Test>>>(UiState.Idle)
    val tests: StateFlow<UiState<List<Test>>> = _tests
    private val _attemptsCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val attemptsCountMap: StateFlow<Map<String, Int>> = _attemptsCountMap
    private val _stats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats: StateFlow<Map<String, Int>> = _stats
    private val _createState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val createState: StateFlow<UiState<String>> = _createState
    private val _platformSettings = MutableStateFlow<UiState<Map<String, Any>>>(UiState.Idle)
    val platformSettings: StateFlow<UiState<Map<String, Any>>> = _platformSettings
    private val _paymentState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val paymentState: StateFlow<UiState<Unit>> = _paymentState
    private val _brandingUploadState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val brandingUploadState: StateFlow<UiState<String>> = _brandingUploadState

    val currentUser = MutableStateFlow<User?>(null)
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null

    init { checkExistingLogin() }

    fun checkExistingLogin() {
        val uid = repo.currentUserId()
        if (uid != null) {
            _loginState.value = UiState.Loading
            userListener?.remove()
            userListener = repo.observeUser(uid) { updatedUser ->
                if (updatedUser != null) {
                    val normalized = updatedUser.withNormalizedTier()
                    currentUser.value = normalized
                    _loginState.value = UiState.Success(normalized)
                } else { _loginState.value = UiState.Idle }
            }
        }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _loginState.value = UiState.Loading
        val result = repo.loginInstructor(email, password)
        if (result.isSuccess) {
            val u = result.getOrNull()!!.withNormalizedTier()
            currentUser.value = u
            userListener?.remove()
            userListener = repo.observeUser(u.uid) { user ->
                user?.let { currentUser.value = it.withNormalizedTier() }
            }
        }
        _loginState.value = if (result.isSuccess) {
            UiState.Success(currentUser.value ?: result.getOrNull()!!.withNormalizedTier())
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Login failed")
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        _loginState.value = UiState.Loading
        val result = repo.sendPasswordReset(email)
        _loginState.value = UiState.Idle
        if (result.isSuccess) {
            onSuccess()
        } else {
            val err = result.exceptionOrNull()?.message ?: "Failed to send reset link."
            onError(err)
        }
    }

    fun register(email: String, password: String, name: String) = viewModelScope.launch {
        _loginState.value = UiState.Loading
        val result = repo.registerInstructor(email, password, name)
        _loginState.value = if (result.isSuccess) {
            val u = result.getOrNull()!!
            currentUser.value = u
            userListener?.remove()
            userListener = repo.observeUser(u.uid) { user ->
                user?.let { currentUser.value = it.withNormalizedTier() }
            }
            UiState.Success(u)
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
    }


    fun loadTests() = viewModelScope.launch {
        _tests.value = UiState.Loading
        val result = repo.getInstructorTests()
        if (result.isSuccess) {
            val testsList = result.getOrNull()!!
            _tests.value = UiState.Success(testsList)
            val counts = mutableMapOf<String, Int>()
            testsList.forEach { t -> counts[t.testId] = repo.getAttemptsCountForTest(t.testId).getOrNull() ?: 0 }
            _attemptsCountMap.value = counts
        } else { _tests.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed") }
    }

    fun createTest(test: Test) = viewModelScope.launch {
        _createState.value = UiState.Loading
        val result = repo.createTest(LatexRender.normalizeTest(test))
        _createState.value = if (result.isSuccess) UiState.Success(result.getOrNull()!!)
        else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun logout() {
        userListener?.remove()
        currentUser.value = null
        repo.logout()
        _loginState.value = UiState.Idle
        _tests.value = UiState.Idle
    }

    fun resetCreateState() {
        _createState.value = UiState.Idle
    }

    fun resetBrandingUploadState() {
        _brandingUploadState.value = UiState.Idle
    }

    fun uploadBrandingLogo(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        _brandingUploadState.value = UiState.Loading
        // Firestore compressed logo (works on Spark — no Firebase Storage / Blaze required)
        val upload = repo.saveBrandingLogoToFirestore(context, uri)
        if (upload.isFailure) {
            _brandingUploadState.value = UiState.Error(upload.exceptionOrNull()?.message ?: "Upload failed")
            return@launch
        }
        val url = upload.getOrNull()!!
        val save = repo.updateInstructorBranding(uid, logoUrl = url)
        _brandingUploadState.value = if (save.isSuccess) UiState.Success(url)
        else UiState.Error(save.exceptionOrNull()?.message ?: "Failed to save logo URL")
    }

    fun saveResultsBranding(
        testId: String,
        headerTitle: String,
        conductedBy: String,
        logoUrl: String?
    ) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        repo.updateInstructorBranding(
            uid,
            logoUrl = logoUrl,
            resultsTitle = headerTitle,
            conductedBy = conductedBy
        )
        if (testId.isNotBlank()) {
            repo.updateTestResultsBranding(
                testId,
                logoUrl = logoUrl,
                headerTitle = headerTitle,
                conductedBy = conductedBy
            )
        }
    }

    private val _editTest = MutableStateFlow<Test?>(null)
    val editTest = _editTest.asStateFlow()

    fun loadTestForEdit(testId: String) = viewModelScope.launch {
        if (testId.isBlank()) return@launch
        val result = repo.getTestForInstructor(testId)
        val test = result.getOrNull()
        if (test != null) {
            // Refresh student join snapshot (fixes NOT_FOUND for existing exams)
            repo.syncPublicTestSnapshot(test)
        }
        _editTest.value = test
    }

    fun clearEditTest() {
        _editTest.value = null
    }

    fun loadStats() = viewModelScope.launch {
        val result = repo.getInstructorStats()
        if (result.isSuccess) {
            _stats.value = result.getOrNull() ?: emptyMap()
        }
    }

    fun updateTest(test: Test) = viewModelScope.launch {
        _createState.value = UiState.Loading
        val result = repo.updateTest(LatexRender.normalizeTest(test))
        _createState.value = if (result.isSuccess) UiState.Success(test.testId)
        else UiState.Error(result.exceptionOrNull()?.message ?: "Failed to update test")
    }

    fun deleteTest(testId: String) = viewModelScope.launch {
        val result = repo.deleteTest(testId)
        if (result.isSuccess) {
            loadTests()
        }
    }

    fun toggleTest(testId: String, enabled: Boolean) = viewModelScope.launch {
        val result = repo.toggleTestEnabled(testId, enabled)
        if (result.isSuccess) {
            loadTests()
        }
    }

    private val _instructorBatches = MutableStateFlow<UiState<List<com.examsystem.app.data.models.Batch>>>(UiState.Idle)
    val instructorBatches: StateFlow<UiState<List<com.examsystem.app.data.models.Batch>>> = _instructorBatches.asStateFlow()

    fun loadInstructorBatches(instituteId: String) = viewModelScope.launch {
        if (instituteId.isBlank()) {
            _instructorBatches.value = UiState.Success(emptyList())
            return@launch
        }
        _instructorBatches.value = UiState.Loading
        val result = repo.getBatches(instituteId)
        _instructorBatches.value = if (result.isSuccess) UiState.Success(result.getOrNull()!!)
        else UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load batches")
    }

    fun loadPlatformSettings() = viewModelScope.launch {
        _platformSettings.value = UiState.Loading
        val result = repo.getPlatformSettings()
        if (result.isSuccess) {
            val settings = result.getOrNull()!!
            ExamSchedule.applyPlatformSettings(settings)
            _platformSettings.value = UiState.Success(settings)
        } else {
            _platformSettings.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load platform settings")
        }
    }

    suspend fun formatMcqWithAi(prompt: String, modelId: String): String {
        val result = repo.formatMcqWithAi(prompt, modelId)
        return result.getOrNull()
            ?: "Error: ${result.exceptionOrNull()?.message ?: "AI formatting failed. Add OpenRouter key in Super Admin → Settings."}"
    }

    fun submitPayment(
        context: Context,
        uri: android.net.Uri,
        plan: String,
        referenceNumber: String,
        mimeType: String
    ) = viewModelScope.launch {
        _paymentState.value = UiState.Loading
        val uploadResult = repo.uploadPaymentReceipt(context, uri, mimeType)
        if (uploadResult.isSuccess) {
            val base64Receipt = uploadResult.getOrNull() ?: ""
            val email = currentUser.value?.email ?: ""
            val payment = Payment(
                userEmail = email,
                plan = com.examsystem.app.data.InstructorTier.normalizeTierKey(plan),
                screenshotUrl = base64Receipt,
                referenceNumber = referenceNumber,
                status = "pending"
            )
            val result = repo.submitPayment(payment)
            _paymentState.value = if (result.isSuccess) UiState.Success(Unit)
            else UiState.Error(result.exceptionOrNull()?.message ?: "Failed to submit payment")
        } else {
            _paymentState.value = UiState.Error(uploadResult.exceptionOrNull()?.message ?: "Failed to upload receipt")
        }
    }
}

data class StudentAttemptItem(
    val attempt: Attempt,
    val testTitle: String,
    val totalMarks: Int,
    val passingMarks: Int,
    val releaseScoreMode: String,
    val resultReleaseTime: com.google.firebase.Timestamp?,
    val resultsReleasedEarly: Boolean
)

// ─── Student ViewModel ────────────────────────────────────────────────────────
class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = FirebaseRepository()
    private val prefs = application.getSharedPreferences("student_prefs", Context.MODE_PRIVATE)

    private val _testState = MutableStateFlow<UiState<Test>>(UiState.Idle)
    val testState: StateFlow<UiState<Test>> = _testState

    private val _myAttempts = MutableStateFlow<UiState<List<StudentAttemptItem>>>(UiState.Idle)
    val myAttempts: StateFlow<UiState<List<StudentAttemptItem>>> = _myAttempts.asStateFlow()

    fun loadMyAttempts() = viewModelScope.launch {
        _myAttempts.value = UiState.Loading
        
        // ─── One-time migration from Device ID query to SharedPreferences ───────
        val migrated = prefs.getBoolean("attempts_migrated", false)
        if (!migrated) {
            val deviceId = android.provider.Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
            if (deviceId.isNotBlank()) {
                val migrationResult = repo.getAttemptsByDeviceId(deviceId)
                if (migrationResult.isSuccess) {
                    val attemptsList = migrationResult.getOrNull() ?: emptyList()
                    val attemptIds = prefs.getStringSet("attempt_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                    attemptsList.forEach { attempt ->
                        attemptIds.add(attempt.attemptId)
                        prefs.edit().putString("attempt_for_${attempt.testId}", attempt.attemptId).apply()
                    }
                    prefs.edit()
                        .putStringSet("attempt_ids", attemptIds)
                        .putBoolean("attempts_migrated", true)
                        .apply()
                } else {
                    // Fail silently to tolerate new security rules (which prevent anonymous client list queries)
                    prefs.edit().putBoolean("attempts_migrated", true).apply()
                }
            }
        }

        val attemptIds = prefs.getStringSet("attempt_ids", emptySet()) ?: emptySet()
        if (attemptIds.isEmpty()) {
            _myAttempts.value = UiState.Success(emptyList())
            return@launch
        }

        val items = attemptIds.map { attemptId ->
            viewModelScope.async {
                val attemptResult = repo.getAttemptById(attemptId)
                val attempt = attemptResult.getOrNull()
                if (attempt != null) {
                    val testResult = repo.getTestByTestId(attempt.testId)
                    val test = testResult.getOrNull()
                    StudentAttemptItem(
                        attempt = attempt,
                        testTitle = test?.title ?: "Exam ${attempt.testId}",
                        totalMarks = test?.totalMarks ?: 0,
                        passingMarks = test?.passingMarks ?: 0,
                        releaseScoreMode = test?.releaseScoreMode ?: "table_only",
                        resultReleaseTime = test?.resultReleaseTime,
                        resultsReleasedEarly = test?.resultsReleasedEarly ?: false
                    )
                } else {
                    null
                }
            }
        }.mapNotNull { it.await() }.sortedByDescending { it.attempt.submittedAt }
        
        _myAttempts.value = UiState.Success(items)
    }

    private val _submitState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val submitState: StateFlow<UiState<Unit>> = _submitState

    private val _testAttempts = MutableStateFlow<UiState<List<Attempt>>>(UiState.Idle)
    val testAttempts: StateFlow<UiState<List<Attempt>>> = _testAttempts

    private val _platformSettings = MutableStateFlow<UiState<Map<String, Any>>>(UiState.Idle)
    val platformSettings: StateFlow<UiState<Map<String, Any>>> = _platformSettings
    private val _paymentState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val paymentState: StateFlow<UiState<Unit>> = _paymentState

    private val _paymentApproved = MutableStateFlow(false)
    val paymentApproved: StateFlow<Boolean> = _paymentApproved

    private val _currentAttempt = MutableStateFlow<Attempt?>(null)
    val currentAttempt: StateFlow<Attempt?> = _currentAttempt
    private var attemptListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var paymentPollJob: kotlinx.coroutines.Job? = null

    private val _secureResultState = MutableStateFlow<SecureResultState>(SecureResultState.Idle)
    val secureResultState: StateFlow<SecureResultState> = _secureResultState

    var currentTest: Test? = null
    var studentName = ""
    var fatherName = ""
    var district = ""
    var gender = ""
    var batchStudentId = ""
    var studentRoll = ""
    var adWatchedInSession = false
        private set
    var cheatAlertCount = 0
        private set
    private val cheatEvents = mutableListOf<String>()
    private val _cheatAlertCount = MutableStateFlow(0)
    val cheatAlertCountFlow: StateFlow<Int> = _cheatAlertCount.asStateFlow()
    private var lastViolationTimeMs = 0L
    private var lastViolationName: String? = null

    fun recordCheatViolation(violation: com.examsystem.app.util.AntiCheatViolation) {
        val now = System.currentTimeMillis()
        if (violation.name == lastViolationName && now - lastViolationTimeMs < 3_000L) return
        lastViolationTimeMs = now
        lastViolationName = violation.name
        cheatAlertCount++
        _cheatAlertCount.value = cheatAlertCount
        cheatEvents.add(violation.name)
    }

    fun cheatEventsSnapshot(): List<String> = cheatEvents.toList()

    private fun resetCheatTracking() {
        cheatAlertCount = 0
        _cheatAlertCount.value = 0
        cheatEvents.clear()
        lastViolationTimeMs = 0L
        lastViolationName = null
    }

    /** Call when the student taps Start Exam — fresh violation log, stable shuffle seed. */
    fun beginExamSession(testId: String) {
        resetCheatTracking()
        getOrCreateShuffleSeed(testId)
    }

    fun getOrCreateShuffleSeed(testId: String): Long {
        val key = "shuffle_seed_$testId"
        val existing = prefs.getLong(key, -1L)
        if (existing >= 0L) return existing
        val seed = System.currentTimeMillis()
        prefs.edit().putLong(key, seed).apply()
        return seed
    }

    private fun clearExamSession(testId: String) {
        prefs.edit()
            .remove("progress_$testId")
            .remove("shuffle_seed_$testId")
            .apply()
    }
    var lastAttempt: Attempt? = null

    fun startObservingAttempt(attemptId: String) {
        attemptListener?.remove()
        attemptListener = repo.observeAttempt(attemptId) { attempt ->
            if (attempt != null) {
                val wasPaid = _currentAttempt.value?.hasPaidForDetails == true
                _currentAttempt.value = attempt
                lastAttempt = attempt
                if (attempt.hasPaidForDetails && !wasPaid) {
                    markPaymentApproved(attempt)
                }
            }
        }
    }

    private fun markPaymentApproved(attempt: Attempt) {
        _currentAttempt.value = attempt
        lastAttempt = attempt
        _paymentApproved.value = true
        stopPaymentApprovalPolling()
        val testId = attempt.testId.ifBlank { currentTest?.testId ?: prefs.getString("last_attempt_test_id", null) }
        viewModelScope.launch {
            if (currentTest == null && !testId.isNullOrBlank()) {
                repo.getTestByTestId(testId!!).getOrNull()?.let { currentTest = it }
            }
            val test = currentTest ?: return@launch
            val id = attempt.attemptId.ifBlank { prefs.getString("last_attempt_id", null) ?: return@launch }
            loadResultsSecurely(test.testId, id, adWatched = true)
        }
    }

    /** Call when payment screen opens — listener + immediate status check. */
    fun ensurePaymentWatch() {
        val attemptId = lastAttempt?.attemptId?.takeIf { it.isNotBlank() }
            ?: prefs.getString("last_attempt_id", null)?.takeIf { it.isNotBlank() }
            ?: return
        startObservingAttempt(attemptId)
        viewModelScope.launch {
            if (checkAttemptPaid(attemptId)) return@launch
            if (_paymentState.value is UiState.Success) {
                startPaymentApprovalPolling()
            }
        }
    }

    fun refreshResultsAfterPayment() {
        val attemptId = _currentAttempt.value?.attemptId?.takeIf { it.isNotBlank() }
            ?: prefs.getString("last_attempt_id", null)?.takeIf { it.isNotBlank() }
            ?: return
        viewModelScope.launch {
            checkAttemptPaid(attemptId)
        }
    }

    private suspend fun checkAttemptPaid(attemptId: String): Boolean {
        val attempt = repo.getAttemptById(attemptId).getOrNull() ?: return false
        return if (attempt.hasPaidForDetails) {
            markPaymentApproved(attempt)
            true
        } else false
    }

    /** Poll attempt doc while student waits on payment screen after submitting receipt. */
    fun startPaymentApprovalPolling() {
        paymentPollJob?.cancel()
        val attemptId = lastAttempt?.attemptId?.takeIf { it.isNotBlank() }
            ?: prefs.getString("last_attempt_id", null)?.takeIf { it.isNotBlank() }
            ?: return
        startObservingAttempt(attemptId)
        paymentPollJob = viewModelScope.launch {
            repeat(120) { // ~6 min, check every 3s
                if (it > 0) kotlinx.coroutines.delay(3000)
                if (checkAttemptPaid(attemptId)) return@launch
            }
        }
    }

    fun stopPaymentApprovalPolling() {
        paymentPollJob?.cancel()
        paymentPollJob = null
    }

    fun loadSavedProfile() {
        studentName = prefs.getString("student_name", "") ?: ""
        fatherName  = prefs.getString("father_name", "") ?: ""
        district    = prefs.getString("district", "") ?: ""
        gender      = prefs.getString("gender", "") ?: ""
        batchStudentId = prefs.getString("batch_student_id", "") ?: ""
        studentRoll = prefs.getString("student_roll", "") ?: ""
    }

    fun saveProfile() {
        prefs.edit()
            .putString("student_name", studentName)
            .putString("father_name", fatherName)
            .putString("district", district)
            .putString("gender", gender)
            .putString("batch_student_id", batchStudentId)
            .putString("student_roll", studentRoll)
            .apply()
    }

    fun hasCompleteProfile(): Boolean =
        studentName.isNotBlank() && fatherName.isNotBlank() &&
            district.isNotBlank() && gender.isNotBlank()

    fun applyProfileFromAttempt(attempt: Attempt) {
        if (attempt.studentName.isNotBlank()) studentName = attempt.studentName
        if (attempt.fatherName.isNotBlank()) fatherName = attempt.fatherName
        if (attempt.district.isNotBlank()) district = attempt.district
        if (attempt.gender.isNotBlank()) gender = attempt.gender
        if (attempt.batchStudentId.isNotBlank()) batchStudentId = attempt.batchStudentId
        saveProfile()
    }

    fun selectRosterStudent(entry: com.examsystem.app.data.models.TestRosterStudent) {
        batchStudentId = entry.studentId
        studentName = entry.name
        studentRoll = entry.rollNumber
        if (entry.fatherName.isNotBlank()) fatherName = entry.fatherName
        if (entry.district.isNotBlank()) district = entry.district
        if (entry.gender.isNotBlank()) gender = entry.gender
        saveProfile()
    }

    /** Skip form when profile saved and (no class roster OR same device already picked). */
    fun canSkipStudentForm(test: com.examsystem.app.data.models.Test): Boolean {
        if (!hasCompleteProfile()) return false
        if (test.instituteId.isBlank() || test.batchId.isBlank()) return true
        if (batchStudentId.isBlank()) return false
        return test.roster.any { it.studentId == batchStudentId }
    }

    suspend fun ensureRegisteredOnBatch(test: com.examsystem.app.data.models.Test): Result<Unit> {
        if (test.instituteId.isBlank() || test.batchId.isBlank()) return Result.success(Unit)
        val deviceId = android.provider.Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""
        val result = repo.findOrCreateBatchStudent(
            instituteId = test.instituteId,
            batchId = test.batchId,
            linkedTestId = test.testId,
            name = studentName,
            rollNumber = studentRoll,
            fatherName = fatherName,
            district = district,
            gender = gender,
            deviceId = deviceId
        )
        if (result.isSuccess) {
            batchStudentId = result.getOrNull().orEmpty().ifBlank { batchStudentId }
            saveProfile()
        }
        return if (result.isSuccess) Result.success(Unit)
        else Result.failure(result.exceptionOrNull() ?: Exception("Could not link to class roster"))
    }

    fun saveProgress(testId: String, answers: Map<String, String>) {
        val json = JSONObject(answers).toString()
        prefs.edit().putString("progress_$testId", json).apply()
    }

    fun loadProgress(testId: String): Map<String, String> {
        val json = prefs.getString("progress_$testId", null) ?: return emptyMap()
        return try {
            val map = mutableMapOf<String, String>()
            val jsonObj = JSONObject(json)
            jsonObj.keys().forEach { map[it] = jsonObj.getString(it) }
            map
        } catch (e: Exception) { emptyMap() }
    }

    fun fetchTest(testId: String) = viewModelScope.launch {
        _testState.value = UiState.Loading
        val result = repo.getTestByTestId(testId)
        if (result.isSuccess) {
            currentTest = result.getOrNull()
            _testState.value = UiState.Success(currentTest!!)
        } else { _testState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Test not found") }
    }

    /** Reload exam schedule from server, then re-check result access (for students waiting on release). */
    fun refreshTestAndResults(testId: String, attemptId: String, adWatched: Boolean) = viewModelScope.launch {
        repo.getTestByTestId(testId).getOrNull()?.let { currentTest = it }
        loadResultsSecurely(testId, attemptId, adWatched)
    }

    fun loadResultsSecurely(testId: String, attemptId: String, adWatched: Boolean) = viewModelScope.launch {
        if (adWatched) {
            adWatchedInSession = true
        }
        val actualAdWatched = adWatched || adWatchedInSession
        _secureResultState.value = SecureResultState.Loading
        val test = currentTest
        val attempt = _currentAttempt.value
        val result = repo.getStudentResultsSecure(testId, attemptId, actualAdWatched)
        if (result.isFailure) {
            // Cloud Functions often unavailable on Spark — use attempt + test already on device
            if (test != null && attempt != null) {
                val paid = attempt.hasPaidForDetails || test.releaseScoreMode == "full_answers"
                val testForState = if (paid) enrichTestWithAnswerKey(test, attempt) else test
                _secureResultState.value = buildLocalResultState(testForState, attempt, actualAdWatched)
                return@launch
            }
            val msg = result.exceptionOrNull()?.message ?: "Failed to load results."
            _secureResultState.value = if (msg.contains("not yet available", ignoreCase = true))
                SecureResultState.NotYetReleased(msg)
            else
                SecureResultState.Error(msg)
            return@launch
        }
        @Suppress("UNCHECKED_CAST")
        val data = result.getOrNull()!!
        when (data["status"] as? String) {
            "ad_required" -> _secureResultState.value = SecureResultState.AdRequired
            "summary" -> _secureResultState.value = SecureResultState.Summary(
                totalScore  = (data["totalScore"]  as? Number)?.toInt() ?: 0,
                totalMarks  = (data["totalMarks"]  as? Number)?.toInt() ?: 0,
                percentage  = (data["percentage"]  as? Number)?.toInt() ?: 0,
                passed      = data["passed"]  as? Boolean ?: false,
                rank        = (data["rank"]    as? Number)?.toInt() ?: 0,
                studentName = data["studentName"] as? String ?: ""
            )
            "full" -> {
                @Suppress("UNCHECKED_CAST")
                val sectionsRaw   = data["sections"]      as? List<Map<String, Any?>>  ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val sectionScores = (data["sectionScores"] as? Map<String, Any?>)?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 } ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val answers       = (data["answers"]       as? Map<String, Any?>)?.mapValues { (_, v) -> v as? String ?: "" } ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val leaderboard   = (data["leaderboard"]   as? List<Map<String, Any?>> ?: emptyList()).map { e ->
                    @Suppress("UNCHECKED_CAST")
                    val secScores = (e["sectionScores"] as? Map<String, Any?>)?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 } ?: emptyMap()
                    LeaderboardEntry(
                        rank        = (e["rank"]        as? Number)?.toInt() ?: 0,
                        studentName = e["studentName"] as? String ?: "",
                        fatherName  = e["fatherName"]  as? String ?: "",
                        district    = e["district"]    as? String ?: "",
                        gender      = e["gender"]      as? String ?: "",
                        totalScore  = (e["totalScore"]  as? Number)?.toInt() ?: 0,
                        sectionScores = secScores,
                        isMe        = e["isMe"]         as? Boolean ?: false
                    )
                }
                val sections = sectionsRaw.map { sec ->
                    @Suppress("UNCHECKED_CAST")
                    val questionsRaw = sec["questions"] as? List<Map<String, Any?>> ?: emptyList()
                    com.examsystem.app.data.models.Section(
                        id    = sec["id"] as? String ?: "",
                        title = sec["title"] as? String ?: "",
                        questions = questionsRaw.map { q ->
                            com.examsystem.app.data.models.Question(
                                id            = q["id"] as? String ?: "",
                                text          = q["text"] as? String ?: "",
                                imageUrl      = q["imageUrl"] as? String ?: "",
                                optionA       = q["optionA"] as? String ?: "",
                                optionB       = q["optionB"] as? String ?: "",
                                optionC       = q["optionC"] as? String ?: "",
                                optionD       = q["optionD"] as? String ?: "",
                                correctAnswer = q["correctAnswer"] as? String ?: "",
                                marks         = (q["marks"] as? Number)?.toInt() ?: 1
                            )
                        }
                    )
                }
                _secureResultState.value = SecureResultState.Full(
                    totalScore    = (data["totalScore"] as? Number)?.toInt() ?: 0,
                    totalMarks    = (data["totalMarks"] as? Number)?.toInt() ?: 0,
                    percentage    = (data["percentage"] as? Number)?.toInt() ?: 0,
                    passed        = data["passed"]       as? Boolean ?: false,
                    rank          = (data["rank"]        as? Number)?.toInt() ?: 0,
                    studentName   = data["studentName"]  as? String ?: "",
                    sectionScores = sectionScores,
                    answers       = answers,
                    sections      = sections,
                    leaderboard   = leaderboard
                )
            }
            else -> _secureResultState.value = SecureResultState.Error("Unknown result status.")
        }
    }

    suspend fun checkExistingAttempt(testId: String): Attempt? {
        val savedAttemptId = prefs.getString("attempt_for_$testId", null)
        if (savedAttemptId != null) {
            val result = repo.getAttemptById(savedAttemptId)
            if (result.isSuccess) {
                val attempt = result.getOrNull()
                if (attempt != null) {
                    if (currentTest == null || !currentTest!!.testId.equals(testId, ignoreCase = true)) {
                        repo.getTestByTestId(testId).getOrNull()?.let { currentTest = it }
                    }
                    applyProfileFromAttempt(attempt)
                    _currentAttempt.value = attempt
                    lastAttempt = attempt
                    _submitState.value = UiState.Success(Unit)
                    startObservingAttempt(attempt.attemptId)
                    return attempt
                }
            }
        }
        return null
    }

    private fun computeSectionScores(test: Test, attempt: Attempt): Map<String, Int> {
        val fromAttempt = attempt.sectionScores.filterKeys { key ->
            test.sections.any { it.id == key }
        }
        if (fromAttempt.isNotEmpty()) return fromAttempt

        val computed = mutableMapOf<String, Int>()
        test.sections.forEach { section ->
            var sectionScore = 0
            section.questions.forEach { q ->
                val student = (attempt.answers[q.id] ?: "").trim().uppercase().take(1)
                val correct = q.correctAnswer.trim().uppercase().take(1)
                    .ifBlank { attempt.correctAnswers[q.id]?.trim()?.uppercase()?.take(1) ?: "" }
                if (student.isNotBlank() && student == correct) {
                    sectionScore += q.marks
                }
            }
            computed[section.id] = sectionScore
        }
        return computed
    }

    /** Merges correctAnswer onto questions from attempt doc or tests_answerkeys. */
    private suspend fun enrichTestWithAnswerKey(test: Test, attempt: Attempt): Test {
        val keys = attempt.correctAnswers
            .filterValues { it.isNotBlank() }
            .ifEmpty { repo.getAnswerKeyForTest(test.testId).getOrNull() ?: emptyMap() }
        if (keys.isEmpty()) return test
        return test.copy(
            sections = test.sections.map { sec ->
                sec.copy(
                    questions = sec.questions.map { q ->
                        val letter = keys[q.id]?.trim()?.uppercase()?.take(1)
                            ?: q.correctAnswer.trim().uppercase().take(1)
                        q.copy(correctAnswer = letter)
                    }
                )
            }
        )
    }

    /** Offline / no-Cloud-Functions fallback using data already loaded for this attempt. */
    private fun buildLocalResultState(test: Test, attempt: Attempt, adWatched: Boolean): SecureResultState {
        if (!ResultsRelease.isReleased(test)) {
            return SecureResultState.NotYetReleased(ResultsRelease.lockedMessage(test))
        }
        val totalMarks = test.totalMarks.coerceAtLeast(1)
        val totalScore = attempt.totalScore
        val pct = (totalScore * 100) / totalMarks
        val passed = totalScore >= test.passingMarks
        val paid = attempt.hasPaidForDetails || test.releaseScoreMode == "full_answers"

        if (!paid && !adWatched) return SecureResultState.AdRequired

        // Paid: section-wise scores, detailed answers, leaderboard (when server provides it)
        if (paid) {
            return SecureResultState.Full(
                totalScore = totalScore,
                totalMarks = totalMarks,
                percentage = pct,
                passed = passed,
                rank = attempt.rank,
                studentName = attempt.studentName,
                sectionScores = computeSectionScores(test, attempt),
                answers = attempt.answers,
                sections = test.sections,
                leaderboard = emptyList()
            )
        }

        // Ad watched, not paid: own total score / pass-fail only — no other students, no sections
        return SecureResultState.Summary(
            totalScore = totalScore,
            totalMarks = totalMarks,
            percentage = pct,
            passed = passed,
            rank = attempt.rank,
            studentName = attempt.studentName
        )
    }

    fun loadTestAttempts(testId: String) = viewModelScope.launch {
        _testAttempts.value = UiState.Loading
        val result = repo.getAttemptsForStudent(testId)
        if (result.isSuccess) {
            _testAttempts.value = UiState.Success(result.getOrNull() ?: emptyList())
        } else {
            _testAttempts.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load results")
        }
    }

    fun submitExam(answers: Map<String, String>) = viewModelScope.launch {
        _submitState.value = UiState.Loading
        val test = currentTest ?: return@launch
        
        // ─── Timing Constraint Check (8:00 AM – 8:00 PM Pakistan time) ───
        if (!ExamSchedule.isPortalOpenPkt()) {
            _submitState.value = UiState.Error(ExamSchedule.portalClosedMessage())
            return@launch
        }
        
        // Calculate Score & Section Scores
        var totalScore = 0
        val sectionScores = mutableMapOf<String, Int>()
        test.sections.orEmpty().forEach { section ->
            var sectionScore = 0
            section.questions.orEmpty().forEach { q ->
                if (answers[q.id] == q.correctAnswer) {
                    totalScore += q.marks
                    sectionScore += q.marks
                }
            }
            sectionScores[section.title] = sectionScore
            sectionScores[section.id] = sectionScore
        }

        val deviceId = android.provider.Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""

        if (test.instituteId.isNotBlank() && test.batchId.isNotBlank()) {
            ensureRegisteredOnBatch(test)
        }

        val attempt = Attempt(
            testId = test.testId,
            studentName = studentName,
            fatherName = fatherName,
            district = district,
            gender = gender,
            answers = answers,
            sectionScores = sectionScores,
            totalScore = totalScore,
            cheatAlerts = cheatAlertCount,
            cheatEvents = cheatEventsSnapshot(),
            deviceId = deviceId,
            batchStudentId = batchStudentId,
            submittedAt = com.google.firebase.Timestamp.now()
        )
        
        val result = repo.submitAttempt(attempt)
        if (result.isSuccess) {
            val savedAttemptId = result.getOrNull() ?: ""
            val updatedAttempt = attempt.copy(attemptId = savedAttemptId)
            _currentAttempt.value = updatedAttempt
            lastAttempt = updatedAttempt
            clearExamSession(test.testId)
            
            val attemptIds = prefs.getStringSet("attempt_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            attemptIds.add(savedAttemptId)
            
            prefs.edit()
                .putString("last_attempt_id", savedAttemptId)
                .putString("last_attempt_test_id", test.testId)
                .putString("attempt_for_${test.testId}", savedAttemptId)
                .putStringSet("attempt_ids", attemptIds)
                .apply()
            _submitState.value = UiState.Success(Unit)
            startObservingAttempt(savedAttemptId)
        } else { _submitState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Submission failed") }
    }

    fun resetState() {
        stopPaymentApprovalPolling()
        attemptListener?.remove()
        attemptListener = null
        _currentAttempt.value = null
        _testState.value = UiState.Idle
        _submitState.value = UiState.Idle
        _secureResultState.value = SecureResultState.Idle
        _testAttempts.value = UiState.Idle
        _paymentState.value = UiState.Idle
        _paymentApproved.value = false
        resetCheatTracking()
        lastAttempt = null
        currentTest = null
        adWatchedInSession = false
    }

    override fun onCleared() {
        super.onCleared()
        attemptListener?.remove()
    }

    fun loadPlatformSettings() = viewModelScope.launch {
        _platformSettings.value = UiState.Loading
        val result = repo.getPlatformSettings()
        if (result.isSuccess) {
            val settings = result.getOrNull()!!
            ExamSchedule.applyPlatformSettings(settings)
            _platformSettings.value = UiState.Success(settings)
        } else {
            _platformSettings.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load platform settings")
        }
    }

    fun submitPayment(
        context: Context,
        uri: android.net.Uri,
        referenceNumber: String,
        mimeType: String
    ) = viewModelScope.launch {
        // Prefer in-memory values, but fall back to SharedPreferences so payment works
        // even after the app is restarted between exam submission and payment screen.
        val testId = currentTest?.testId
            ?: prefs.getString("last_attempt_test_id", null)
            ?: run {
                _paymentState.value = UiState.Error("Could not find your exam session. Please re-open the results screen and try again.")
                return@launch
            }
        val attemptId = lastAttempt?.attemptId
            ?: prefs.getString("last_attempt_id", null)
            ?: run {
                _paymentState.value = UiState.Error("Could not link payment to your attempt. Please re-open the results screen and try again.")
                return@launch
            }
        _paymentState.value = UiState.Loading
        val uploadResult = repo.uploadPaymentReceipt(context, uri, mimeType)
        if (uploadResult.isSuccess) {
            val base64Receipt = uploadResult.getOrNull() ?: ""
            val payment = Payment(
                paymentType = "student_result",
                testId = testId,
                attemptId = attemptId,
                studentName = studentName,
                userEmail = "", // Optional for student
                plan = "student_result",
                screenshotUrl = base64Receipt,
                referenceNumber = referenceNumber,
                status = "pending"
            )
            val result = repo.submitPayment(payment)
            if (result.isSuccess) {
                _paymentApproved.value = false
                _paymentState.value = UiState.Success(Unit)
                startObservingAttempt(attemptId)
                startPaymentApprovalPolling()
            } else {
                _paymentState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to submit payment")
            }
        } else {
            _paymentState.value = UiState.Error(uploadResult.exceptionOrNull()?.message ?: "Failed to upload receipt")
        }
    }
}

class ResultsViewModel : ViewModel() {
    private val repo = FirebaseRepository()
    private val _attempts = MutableStateFlow<UiState<List<Attempt>>>(UiState.Idle)
    val attempts: StateFlow<UiState<List<Attempt>>> = _attempts

    private val _currentTest = MutableStateFlow<Test?>(null)
    val currentTest: StateFlow<Test?> = _currentTest

    fun loadResults(testId: String) = viewModelScope.launch {
        _attempts.value = UiState.Loading
        val testResult = repo.getTestByTestId(testId)
        if (testResult.isSuccess) {
            _currentTest.value = testResult.getOrNull()
        }
        val result = repo.getAttemptsForTest(testId)
        _attempts.value = if (result.isSuccess) UiState.Success(result.getOrNull()!!)
        else UiState.Error("Failed to load results")
    }
}

// ─── Institute (academy / school) ─────────────────────────────────────────────
class InstituteViewModel : ViewModel() {
    private val repo = FirebaseRepository()

    private val _institute = MutableStateFlow<Institute?>(null)
    val institute: StateFlow<Institute?> = _institute.asStateFlow()

    private val _memberRole = MutableStateFlow("")
    val memberRole: StateFlow<String> = _memberRole.asStateFlow()

    private val _stats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats: StateFlow<Map<String, Int>> = _stats.asStateFlow()

    private val _members = MutableStateFlow<UiState<List<InstituteMember>>>(UiState.Idle)
    val members: StateFlow<UiState<List<InstituteMember>>> = _members.asStateFlow()

    private val _batches = MutableStateFlow<UiState<List<Batch>>>(UiState.Idle)
    val batches: StateFlow<UiState<List<Batch>>> = _batches.asStateFlow()

    private val _students = MutableStateFlow<UiState<List<BatchStudent>>>(UiState.Idle)
    val students: StateFlow<UiState<List<BatchStudent>>> = _students.asStateFlow()

    private val _attendance = MutableStateFlow<AttendanceSession?>(null)
    val attendance: StateFlow<AttendanceSession?> = _attendance.asStateFlow()

    private val _attendanceHistory = MutableStateFlow<UiState<List<AttendanceSession>>>(UiState.Idle)
    val attendanceHistory: StateFlow<UiState<List<AttendanceSession>>> = _attendanceHistory.asStateFlow()

    private val _attendanceLoadError = MutableStateFlow<String?>(null)
    val attendanceLoadError: StateFlow<String?> = _attendanceLoadError.asStateFlow()

    private val _actionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val actionState: StateFlow<UiState<Unit>> = _actionState.asStateFlow()

    private val _instituteLoading = MutableStateFlow(false)
    val instituteLoading: StateFlow<Boolean> = _instituteLoading.asStateFlow()

    private val _instituteError = MutableStateFlow<String?>(null)
    val instituteError: StateFlow<String?> = _instituteError.asStateFlow()

    val isOwner: Boolean get() = _memberRole.value == "owner"

    fun loadInstitute(uid: String) = viewModelScope.launch {
        _instituteLoading.value = true
        _instituteError.value = null
        val result = repo.getInstituteForUser(uid)
        _instituteLoading.value = false
        if (result.isSuccess) {
            val loaded = result.getOrNull()
            _institute.value = loaded
            val iid = loaded?.instituteId
            if (iid != null) {
                var role = repo.getInstituteMemberRole(iid, uid)
                if (role.isBlank() && loaded.ownerUid == uid) role = "owner"
                _memberRole.value = role
                loadStats(iid)
                loadMembers(iid)
            } else {
                _instituteError.value =
                    "Institute not set up yet. If you just paid, wait for admin approval, then open this screen again."
            }
        } else {
            _instituteError.value = result.exceptionOrNull()?.message ?: "Failed to load institute"
        }
    }

    fun loadStats(instituteId: String) = viewModelScope.launch {
        val result = repo.getInstituteDashboardStats(instituteId)
        if (result.isSuccess) _stats.value = result.getOrNull() ?: emptyMap()
    }

    fun loadMembers(instituteId: String) = viewModelScope.launch {
        _members.value = UiState.Loading
        val result = repo.getInstituteMembers(instituteId)
        _members.value = if (result.isSuccess) UiState.Success(result.getOrNull()!!)
        else UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load")
    }

    fun addMemberByEmail(instituteId: String, email: String) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.addInstituteMemberByEmail(instituteId, email)
        _actionState.value = if (result.isSuccess) {
            loadMembers(instituteId)
            loadStats(instituteId)
            UiState.Success(Unit)
        } else {
            UiState.Error(result.exceptionOrNull()?.message ?: "Failed to add instructor")
        }
    }

    fun addMemberWithCredentials(
        instituteId: String,
        name: String,
        email: String,
        password: String
    ) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.addInstituteMemberWithCredentials(instituteId, name, email, password)
        _actionState.value = if (result.isSuccess) {
            loadMembers(instituteId)
            loadStats(instituteId)
            UiState.Success(Unit)
        } else {
            UiState.Error(result.exceptionOrNull()?.message ?: "Failed to add instructor")
        }
    }

    fun clearActionState() {
        _actionState.value = UiState.Idle
    }

    fun removeMember(instituteId: String, memberUid: String, ownerUid: String) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.removeInstituteMember(instituteId, memberUid, ownerUid)
        _actionState.value = if (result.isSuccess) {
            loadMembers(instituteId)
            loadStats(instituteId)
            UiState.Success(Unit)
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun loadBatches(instituteId: String) = viewModelScope.launch {
        _batches.value = UiState.Loading
        val result = repo.getBatches(instituteId)
        _batches.value = if (result.isSuccess) UiState.Success(result.getOrNull()!!)
        else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun createBatch(instituteId: String, name: String, description: String) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.createBatch(instituteId, name, description)
        _actionState.value = if (result.isSuccess) {
            loadBatches(instituteId)
            loadStats(instituteId)
            UiState.Success(Unit)
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun deleteBatch(instituteId: String, batchId: String) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.deleteBatch(instituteId, batchId)
        _actionState.value = if (result.isSuccess) {
            loadBatches(instituteId)
            loadStats(instituteId)
            UiState.Success(Unit)
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun loadStudents(instituteId: String, batchId: String) = viewModelScope.launch {
        _students.value = UiState.Loading
        val result = repo.getBatchStudents(instituteId, batchId)
        _students.value = if (result.isSuccess) UiState.Success(result.getOrNull()!!)
        else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun addStudent(instituteId: String, batchId: String, name: String, roll: String) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.addBatchStudent(instituteId, batchId, name, roll)
        _actionState.value = if (result.isSuccess) {
            loadStudents(instituteId, batchId)
            loadStats(instituteId)
            UiState.Success(Unit)
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }

    fun loadAttendance(instituteId: String, batchId: String, date: String) = viewModelScope.launch {
        _attendanceLoadError.value = null
        val result = repo.getAttendance(instituteId, batchId, date)
        if (result.isSuccess) {
            _attendance.value = result.getOrNull()
        } else {
            _attendance.value = null
            _attendanceLoadError.value = result.exceptionOrNull()?.message
        }
    }

    fun loadAttendanceHistory(instituteId: String, batchId: String) = viewModelScope.launch {
        _attendanceHistory.value = UiState.Loading
        val result = repo.getAttendanceHistory(instituteId, batchId)
        _attendanceHistory.value = if (result.isSuccess) {
            UiState.Success(result.getOrNull() ?: emptyList())
        } else {
            UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load history")
        }
    }

    fun saveAttendance(
        instituteId: String,
        batchId: String,
        date: String,
        records: Map<String, String>,
        markedBy: String
    ) = viewModelScope.launch {
        _actionState.value = UiState.Loading
        val result = repo.saveAttendance(instituteId, batchId, date, records, markedBy)
        _actionState.value = if (result.isSuccess) {
            loadAttendance(instituteId, batchId, date)
            loadAttendanceHistory(instituteId, batchId)
            UiState.Success(Unit)
        } else UiState.Error(result.exceptionOrNull()?.message ?: "Failed")
    }
}

private fun User.withNormalizedTier() = copy(
    subscriptionTier = com.examsystem.app.data.InstructorTier.normalizeTierKey(subscriptionTier)
)
