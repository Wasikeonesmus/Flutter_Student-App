package com.examsystem.app.data.repository

import com.examsystem.app.data.models.*
import com.examsystem.app.util.ExamSchedule
import com.examsystem.app.util.ResultsRelease
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    // Use no-arg getInstance() — avoids named-database connection errors
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // ─── AUTH ─────────────────────────────────────────────────────────────────

    suspend fun loginInstructor(email: String, password: String): Result<User> {
        return try {
            withTimeout(30_000) {
                val cleanEmail = email.filter { !it.isWhitespace() }.replace("\u200B", "").lowercase()
                val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
                val uid = result.user?.uid ?: return@withTimeout Result.failure(Exception("Auth failed"))
                val doc = db.collection("users").document(uid).get().await()

                // ── Self-healing: Firestore doc missing (orphaned Auth account) ──────
                // This happens when internet drops after Auth succeeds but before
                // the Firestore write completes during registration.
                if (!doc.exists()) {
                    // Re-create the missing Firestore document so the admin can approve them
                    val recoveredUser = User(
                        uid = uid,
                        email = cleanEmail,
                        name = cleanEmail.substringBefore("@"),
                        role = "instructor",
                        approvalStatus = "pending",
                        subscriptionStatus = "inactive",
                        createdAt = com.google.firebase.Timestamp.now()
                    )
                    db.collection("users").document(uid).set(recoveredUser).await()
                    return@withTimeout Result.failure(Exception(
                        "Your account profile was recovered.\nPlease wait for admin approval, then try logging in again."
                    ))
                }

                val user = doc.toObject(User::class.java)
                    ?: return@withTimeout Result.failure(Exception("User data is corrupted. Please contact support."))
                
                // Return success so the UI can navigate to the Payment/Pending screen
                Result.success(user)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Connection timed out. Check your internet and try again."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Incorrect email or password."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Login failed. Please try again."))
        }
    }

    suspend fun registerInstructor(email: String, password: String, name: String): Result<User> {
        var createdUid: String? = null   // Track Auth uid so we can clean up on failure
        return try {
            withTimeout(30_000) {
                val cleanEmail = email.trim().lowercase()
                val cleanName = name.trim()

                // Step 1: Create Firebase Auth account
                val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                createdUid = result.user?.uid
                val uid = createdUid ?: return@withTimeout Result.failure(Exception("Registration failed"))

                // Step 2: Write Firestore document
                // If this fails, we MUST delete the Auth account (cleanup in catch block)
                val newUser = User(
                    uid = uid,
                    email = cleanEmail,
                    name = cleanName,
                    role = "instructor",
                    approvalStatus = "pending",
                    subscriptionStatus = "inactive",
                    createdAt = com.google.firebase.Timestamp.now()
                )
                db.collection("users").document(uid).set(newUser).await()
                createdUid = null  // Firestore write succeeded — no cleanup needed
                Result.success(newUser)
            }
        } catch (e: TimeoutCancellationException) {
            // Clean up orphaned Auth account so the user can retry
            createdUid?.let { tryDeleteAuthUser() }
            Result.failure(Exception("Connection timed out. Your account was not created. Please try again."))
        } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("Password is too weak. Use at least 6 characters."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Invalid email address. Please check and try again."))
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Result.failure(Exception("An account with this email already exists. Try logging in instead."))
        } catch (e: Exception) {
            // Clean up orphaned Auth account so the user can retry
            createdUid?.let { tryDeleteAuthUser() }
            val msg = e.message ?: ""
            val friendly = when {
                msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                    "Firebase is not configured correctly. Please contact support."
                msg.contains("network", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ->
                    "No internet connection. Your account was not created. Please try again."
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Registration blocked by server rules. Please contact support."
                msg.isNotBlank() -> msg
                else -> "Registration failed. Please try again."
            }
            Result.failure(Exception(friendly))
        }
    }

    /** Deletes the currently signed-in Firebase Auth user (cleanup after partial registration). */
    private suspend fun tryDeleteAuthUser() {
        try {
            auth.currentUser?.delete()?.await()
            auth.signOut()
        } catch (_: Exception) {
            // Best-effort cleanup — ignore if it fails
        }
    }

    fun logout() = auth.signOut()

    fun currentUserId() = auth.currentUser?.uid

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim().lowercase()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeUser(uid: String, onUpdate: (User?) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        return try {
            db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    onUpdate(user)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun observeAttempt(attemptId: String, onUpdate: (Attempt?) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        return try {
            db.collection("attempts").document(attemptId).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val attempt = snapshot.toObject(Attempt::class.java)
                        ?.copy(attemptId = snapshot.id)
                    onUpdate(attempt)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ─── TESTS ────────────────────────────────────────────────────────────────

    /**
     * Fetches test via Cloud Function — correct answers are NEVER sent to the device.
     * Spark / free plan: reads tests_public only (no Cloud Functions / Blaze).
     */
    suspend fun getTestByTestId(testId: String): Result<Test> {
        val normalizedId = testId.trim().uppercase()
        if (normalizedId.isBlank()) return Result.failure(Exception("Please enter an exam ID."))
        return getTestFromPublicCollection(normalizedId)
    }

    /** Student-safe exam read (no correct answers) — works without Cloud Functions. */
    private suspend fun getTestFromPublicCollection(normalizedId: String): Result<Test> {
        if (!ExamSchedule.isPortalOpenPkt()) {
            return Result.failure(Exception(ExamSchedule.portalClosedMessage()))
        }
        return try {
            var doc = db.collection("tests_public").document(normalizedId).get().await()
            if (!doc.exists()) {
                val query = db.collection("tests_public")
                    .whereEqualTo("testId", normalizedId)
                    .limit(1)
                    .get().await()
                doc = query.documents.firstOrNull()
                    ?: return Result.failure(Exception(
                        "Exam not found. Check the ID, or ask your instructor to open and save the exam again."
                    ))
            }
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?>
                ?: return Result.failure(Exception("Exam data is corrupted. Contact your instructor."))
            if (data["isEnabled"] == false) {
                return Result.failure(Exception("This exam is currently disabled by the instructor."))
            }
            val mapped = mapToTest(data).copy(testId = doc.id)
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load exam."))
        }
    }

    /** Writes a student-safe copy (no correct answers) for join-by-ID without Cloud Functions. */
    suspend fun syncPublicTestSnapshot(test: Test) {
        val docId = test.testId.trim().uppercase()
        if (docId.isBlank()) return
        val publicSections = test.sections.map { sec ->
            hashMapOf(
                "id" to sec.id,
                "title" to sec.title,
                "questions" to sec.questions.map { q ->
                    hashMapOf(
                        "id" to q.id,
                        "text" to q.text,
                        "imageUrl" to q.imageUrl,
                        "optionA" to q.optionA,
                        "optionB" to q.optionB,
                        "optionC" to q.optionC,
                        "optionD" to q.optionD,
                        "marks" to q.marks
                    )
                }
            )
        }
        val instituteId = test.instituteId.trim()
        val batchId = test.batchId.trim()
        var rosterPayload: List<Map<String, Any>> = emptyList()
        if (instituteId.isNotBlank() && batchId.isNotBlank()) {
            val rosterResult = getBatchStudents(instituteId, batchId)
            if (rosterResult.isSuccess) {
                rosterPayload = rosterResult.getOrNull().orEmpty().map { s ->
                    hashMapOf(
                        "studentId" to s.studentId,
                        "name" to s.name,
                        "rollNumber" to s.rollNumber,
                        "fatherName" to s.fatherName,
                        "district" to s.district,
                        "gender" to s.gender
                    )
                }
            }
        }
        val payload = hashMapOf(
            "testId" to docId,
            "instructorId" to test.instructorId,
            "instituteId" to instituteId,
            "batchId" to batchId,
            "roster" to rosterPayload,
            "title" to test.title,
            "instructions" to test.instructions,
            "durationMinutes" to test.durationMinutes,
            "passingMarks" to test.passingMarks,
            "totalMarks" to test.totalMarks,
            "isEnabled" to test.isEnabled,
            "releaseScoreMode" to test.releaseScoreMode,
            "resultReleaseTime" to test.resultReleaseTime,
            "resultsReleasedEarly" to test.resultsReleasedEarly,
            "antiCheatFullscreen" to test.antiCheatFullscreen,
            "antiCheatDetectLeaveApp" to test.antiCheatDetectLeaveApp,
            "antiCheatBlockCopyPaste" to test.antiCheatBlockCopyPaste,
            "antiCheatBlockScreenshot" to test.antiCheatBlockScreenshot,
            "antiCheatCamera" to test.antiCheatCamera,
            "antiCheatRandomizeQuestions" to test.antiCheatRandomizeQuestions,
            "antiCheatRandomizeOptions" to test.antiCheatRandomizeOptions,
            "antiCheatAutoSubmit" to test.antiCheatAutoSubmit,
            "sections" to publicSections
        )
        db.collection("tests_public").document(docId).set(payload).await()
        syncAnswerKeySnapshot(test)
    }

    /** Answer key only (questionId → letter) — used for paid detailed review without Cloud Functions. */
    suspend fun syncAnswerKeySnapshot(test: Test) {
        val docId = test.testId.trim().uppercase()
        if (docId.isBlank()) return
        val answers = mutableMapOf<String, String>()
        test.sections.forEach { sec ->
            sec.questions.forEach { q ->
                val letter = q.correctAnswer.trim().uppercase().take(1)
                if (q.id.isNotBlank() && letter in listOf("A", "B", "C", "D")) {
                    answers[q.id] = letter
                }
            }
        }
        if (answers.isEmpty()) return
        db.collection("tests_answerkeys").document(docId).set(
            hashMapOf(
                "testId" to docId,
                "instructorId" to test.instructorId,
                "answers" to answers
            )
        ).await()
    }

    suspend fun getAnswerKeyForTest(testId: String): Result<Map<String, String>> {
        val docId = testId.trim().uppercase()
        if (docId.isBlank()) return Result.success(emptyMap())
        return try {
            val doc = db.collection("tests_answerkeys").document(docId).get().await()
            if (!doc.exists()) return Result.success(emptyMap())
            @Suppress("UNCHECKED_CAST")
            val answers = doc.get("answers") as? Map<String, String> ?: emptyMap()
            Result.success(answers.mapValues { (_, v) -> v.trim().uppercase().take(1) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Student results via Firestore only (Spark plan — no Cloud Functions).
     * Map "status": "ad_required" | "summary" | "full"
     */
    suspend fun getStudentResultsSecure(
        testId: String,
        attemptId: String,
        adWatched: Boolean
    ): Result<Map<String, Any?>> = getStudentResultsFromFirestore(testId, attemptId, adWatched)

    private suspend fun getStudentResultsFromFirestore(
        testId: String,
        attemptId: String,
        adWatched: Boolean
    ): Result<Map<String, Any?>> {
        return try {
            val attempt = getAttemptById(attemptId).getOrNull()
                ?: return Result.failure(Exception("Attempt not found."))
            val normalizedId = testId.trim().uppercase()
            val test = getTestFromPublicCollection(normalizedId).getOrNull()
                ?: return Result.failure(Exception("Exam not found."))

            if (!ResultsRelease.isReleased(test)) {
                return Result.failure(Exception(ResultsRelease.lockedMessage(test)))
            }

            val totalMarks = test.totalMarks.coerceAtLeast(1)
            val totalScore = attempt.totalScore
            val pct = (totalScore * 100) / totalMarks
            val passed = totalScore >= test.passingMarks
            val paid = attempt.hasPaidForDetails || test.releaseScoreMode == "full_answers"

            if (!paid && !adWatched) {
                return Result.success(mapOf("status" to "ad_required"))
            }

            val base = mapOf(
                "totalScore" to totalScore,
                "totalMarks" to totalMarks,
                "percentage" to pct,
                "passed" to passed,
                "rank" to attempt.rank,
                "studentName" to attempt.studentName
            )

            if (!paid) {
                return Result.success(base + mapOf("status" to "summary"))
            }

            val answerKey = attempt.correctAnswers
                .filterValues { it.isNotBlank() }
                .ifEmpty { getAnswerKeyForTest(normalizedId).getOrNull() ?: emptyMap() }

            val sectionsPayload = test.sections.map { sec ->
                hashMapOf(
                    "id" to sec.id,
                    "title" to sec.title,
                    "questions" to sec.questions.map { q ->
                        val letter = answerKey[q.id]?.trim()?.uppercase()?.take(1)
                            ?: q.correctAnswer.trim().uppercase().take(1)
                        hashMapOf(
                            "id" to q.id,
                            "text" to q.text,
                            "imageUrl" to q.imageUrl,
                            "optionA" to q.optionA,
                            "optionB" to q.optionB,
                            "optionC" to q.optionC,
                            "optionD" to q.optionD,
                            "correctAnswer" to letter,
                            "marks" to q.marks
                        )
                    }
                )
            }

            val sectionScores = computeSectionScoresForAttempt(test, attempt, answerKey)

            Result.success(
                base + mapOf(
                    "status" to "full",
                    "sectionScores" to sectionScores,
                    "answers" to attempt.answers,
                    "sections" to sectionsPayload,
                    "leaderboard" to emptyList<Any>()
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load results."))
        }
    }

    private fun computeSectionScoresForAttempt(
        test: Test,
        attempt: Attempt,
        answerKey: Map<String, String>
    ): Map<String, Int> {
        val fromAttempt = attempt.sectionScores.filterKeys { key ->
            test.sections.any { it.id == key }
        }
        if (fromAttempt.isNotEmpty()) return fromAttempt

        val computed = mutableMapOf<String, Int>()
        test.sections.forEach { section ->
            var sectionScore = 0
            section.questions.forEach { q ->
                val student = (attempt.answers[q.id] ?: "").trim().uppercase().take(1)
                val correct = answerKey[q.id]?.trim()?.uppercase()?.take(1)
                    ?: q.correctAnswer.trim().uppercase().take(1)
                if (student.isNotBlank() && student == correct) {
                    sectionScore += q.marks
                }
            }
            computed[section.id] = sectionScore
        }
        return computed
    }

    /** Converts the Cloud Function map response back to a Test object. */
    @Suppress("UNCHECKED_CAST")
    private fun mapToTest(data: Map<String, Any?>): Test {
        val sectionsRaw = data["sections"] as? List<Map<String, Any?>> ?: emptyList()
        val sections = sectionsRaw.map { sec ->
            val questionsRaw = sec["questions"] as? List<Map<String, Any?>> ?: emptyList()
            Section(
                id    = sec["id"] as? String ?: "",
                title = sec["title"] as? String ?: "",
                questions = questionsRaw.map { q ->
                    Question(
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
        val releaseMillis = (data["resultReleaseTime"] as? Number)?.toLong()
        return Test(
            testId           = data["testId"] as? String ?: "",
            title            = data["title"] as? String ?: "",
            instructions     = data["instructions"] as? String ?: "",
            durationMinutes  = (data["durationMinutes"] as? Number)?.toInt() ?: 60,
            passingMarks     = (data["passingMarks"] as? Number)?.toInt() ?: 0,
            totalMarks       = (data["totalMarks"] as? Number)?.toInt() ?: 0,
            isEnabled        = data["isEnabled"] as? Boolean ?: true,
            releaseScoreMode = data["releaseScoreMode"] as? String ?: "table_only",
            resultReleaseTime = releaseMillis?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) },
            resultsReleasedEarly = data["resultsReleasedEarly"] as? Boolean ?: false,
            sections         = sections,
            antiCheatFullscreen = mapAntiCheatBool(data, "antiCheatFullscreen"),
            antiCheatDetectLeaveApp = mapAntiCheatBool(data, "antiCheatDetectLeaveApp"),
            antiCheatBlockCopyPaste = mapAntiCheatBool(data, "antiCheatBlockCopyPaste"),
            antiCheatBlockScreenshot = mapAntiCheatBool(data, "antiCheatBlockScreenshot"),
            antiCheatCamera = data["antiCheatCamera"] as? Boolean ?: false,
            antiCheatRandomizeQuestions = mapAntiCheatBool(data, "antiCheatRandomizeQuestions"),
            antiCheatRandomizeOptions = mapAntiCheatBool(data, "antiCheatRandomizeOptions"),
            antiCheatAutoSubmit = mapAntiCheatBool(data, "antiCheatAutoSubmit"),
            instituteId = data["instituteId"] as? String ?: "",
            batchId = data["batchId"] as? String ?: "",
            roster = parseRoster(data["roster"]),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRoster(raw: Any?): List<TestRosterStudent> {
        val list = raw as? List<Map<String, Any?>> ?: return emptyList()
        return list.map { m ->
            TestRosterStudent(
                studentId = m["studentId"] as? String ?: "",
                name = m["name"] as? String ?: "",
                rollNumber = m["rollNumber"] as? String ?: "",
                fatherName = m["fatherName"] as? String ?: "",
                district = m["district"] as? String ?: "",
                gender = m["gender"] as? String ?: ""
            )
        }.filter { it.studentId.isNotBlank() || it.name.isNotBlank() }
    }

    /** Student exam maps omit unset flags; default ON except camera (off unless enabled). */
    private fun mapAntiCheatBool(data: Map<String, Any?>, key: String): Boolean =
        data[key] as? Boolean ?: true

    suspend fun getTestForInstructor(testId: String): Result<Test> {
        return try {
            val normalizedId = testId.trim().uppercase()
            var doc = db.collection("tests").document(normalizedId).get().await()
            if (!doc.exists()) {
                val query = db.collection("tests")
                    .whereEqualTo("testId", normalizedId)
                    .limit(1)
                    .get().await()
                doc = query.documents.firstOrNull() ?: return Result.failure(Exception("Exam not found."))
            }
            val test = doc.toObject(Test::class.java)?.copy(testId = doc.id)
                ?: return Result.failure(Exception("Exam data corrupted."))
            Result.success(test)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInstructorTests(): Result<List<Test>> {
        val uid = currentUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val snapshot = db.collection("tests")
                .whereEqualTo("instructorId", uid)
                .get().await()
            val tests = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Test::class.java)?.copy(testId = doc.id)
            }.sortedByDescending { it.createdAt?.seconds ?: 0 }
            Result.success(tests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttemptsCountForTest(testId: String): Result<Int> {
        return try {
            val snapshot = db.collection("attempts").whereEqualTo("testId", testId).get().await()
            Result.success(snapshot.size())
        } catch (e: Exception) {
            Result.success(0)
        }
    }

    suspend fun createTest(test: Test): Result<String> {
        return try {
            val uniqueId = UUID.randomUUID().toString().substring(0, 8).uppercase()
            val newTest = test.copy(
                testId = uniqueId, 
                instructorId = currentUserId()!!,
                createdAt = com.google.firebase.Timestamp.now()
            )
            db.collection("tests").document(uniqueId).set(newTest).await()
            syncPublicTestSnapshot(newTest)
            Result.success(uniqueId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTest(test: Test): Result<Unit> {
        return try {
            val docId = test.testId.trim().uppercase()
            if (docId.isBlank()) return Result.failure(Exception("Cannot save: exam ID is missing."))
            val toSave = test.copy(testId = docId)
            db.collection("tests").document(docId).set(toSave).await()
            syncPublicTestSnapshot(toSave)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTest(testId: String): Result<Unit> {
        return try {
            val docId = testId.trim().uppercase()
            db.collection("tests").document(docId).delete().await()
            db.collection("tests_public").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleTestEnabled(testId: String, enabled: Boolean): Result<Unit> {
        return try {
            val docId = testId.trim().uppercase()
            db.collection("tests").document(docId).update("isEnabled", enabled).await()
            db.collection("tests_public").document(docId).update("isEnabled", enabled).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── ATTEMPTS ─────────────────────────────────────────────────────────────

    suspend fun submitAttempt(attempt: Attempt): Result<String> {
        return try {
            // 1. Device ID check
            if (attempt.deviceId.isNotBlank()) {
                try {
                    val deviceSnap = db.collection("attempts")
                        .whereEqualTo("testId", attempt.testId)
                        .whereEqualTo("deviceId", attempt.deviceId)
                        .get().await()
                    if (!deviceSnap.isEmpty) {
                        return Result.failure(Exception("You have already submitted the test on this mobile!"))
                    }
                } catch (e: Exception) {
                    // Ignore query permission failures for anonymous/non-instructor students.
                    // Duplicate submissions are restricted locally via SharedPreferences.
                }
            }
            // 2. IP Address check
            if (attempt.ipAddress.isNotBlank()) {
                try {
                    val ipSnap = db.collection("attempts")
                        .whereEqualTo("testId", attempt.testId)
                        .whereEqualTo("ipAddress", attempt.ipAddress)
                        .get().await()
                    if (!ipSnap.isEmpty) {
                        return Result.failure(Exception("You have already submitted the test from this network/IP!"))
                    }
                } catch (e: Exception) {
                    // Ignore query permission failures for anonymous/non-instructor students.
                }
            }

            val id = UUID.randomUUID().toString()
            db.collection("attempts").document(id)
                .set(attempt.copy(attemptId = id)).await()
            // Return the generated id so the caller can store it in lastAttempt
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttemptsForTest(testId: String): Result<List<Attempt>> {
        val uid = currentUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // Verify the instructor owns this test first (security)
            val testDoc = db.collection("tests").document(testId).get().await()
            val test = testDoc.toObject(Test::class.java)
            if (test?.instructorId != uid) return Result.failure(Exception("Unauthorized"))
            val snapshot = db.collection("attempts")
                .whereEqualTo("testId", testId)
                .get().await()
            val attempts = snapshot.documents.mapNotNull { it.toObject(Attempt::class.java) }
                .sortedByDescending { it.totalScore }
            Result.success(attempts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttemptsForStudent(testId: String): Result<List<Attempt>> {
        return try {
            val snapshot = db.collection("attempts")
                .whereEqualTo("testId", testId)
                .get().await()
            val attempts = snapshot.documents.mapNotNull { it.toObject(Attempt::class.java) }
                .sortedByDescending { it.totalScore }
            Result.success(attempts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getExistingAttempt(testId: String, deviceId: String): Result<Attempt?> {
        return try {
            val snapshot = db.collection("attempts")
                .whereEqualTo("testId", testId)
                .whereEqualTo("deviceId", deviceId)
                .get().await()
            val doc = snapshot.documents.firstOrNull()
            val attempt = doc?.toObject(Attempt::class.java)?.copy(attemptId = doc.id)
            Result.success(attempt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttemptById(attemptId: String): Result<Attempt?> {
        return try {
            val doc = db.collection("attempts").document(attemptId.trim()).get().await()
            if (!doc.exists()) return Result.success(null)
            val attempt = doc.toObject(Attempt::class.java)?.copy(attemptId = doc.id)
            Result.success(attempt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttemptsByDeviceId(deviceId: String): Result<List<Attempt>> {
        return try {
            val snapshot = db.collection("attempts")
                .whereEqualTo("deviceId", deviceId)
                .get().await()
            val attempts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Attempt::class.java)?.copy(attemptId = doc.id)
            }.sortedByDescending { it.submittedAt }
            Result.success(attempts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendVerificationCodeToFirestore(email: String, code: String): Result<Unit> {
        return try {
            val cleanEmail = email.trim().lowercase()
            val data = mapOf(
                "email" to cleanEmail,
                "code" to code,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            val documentId = "${cleanEmail}_${code}"
            db.collection("verification_codes").document(documentId).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkVerificationCode(email: String, code: String): Result<Boolean> {
        return try {
            val cleanEmail = email.trim().lowercase()
            val documentId = "${cleanEmail}_${code}"
            val doc = db.collection("verification_codes").document(documentId).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── INSTRUCTOR DASHBOARD STATS ──────────────────────────────────────────

    suspend fun getInstructorStats(): Result<Map<String, Int>> {
        val uid = currentUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val tests = db.collection("tests").whereEqualTo("instructorId", uid).get().await()
            val active = tests.documents.count { it.getBoolean("isEnabled") == true }
            // Count total attempts across all instructor's tests
            val testIds = tests.documents.map { it.id }
            var totalAttempts = 0
            testIds.forEach { testId ->
                val count = db.collection("attempts").whereEqualTo("testId", testId).get().await().size()
                totalAttempts += count
            }
            Result.success(mapOf(
                "totalTests" to tests.size(),
                "activeExams" to active,
                "totalAttempts" to totalAttempts
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // ─── PLATFORM SETTINGS & PAYMENTS ─────────────────────────────────────────

    suspend fun getPlatformSettings(): Result<Map<String, Any>> {
        return try {
            val doc = db.collection("platform_settings").document("global").get().await()
            if (doc.exists() && doc.data != null) {
                Result.success(doc.data!!)
            } else {
                // Fallback defaults matching Super Admin React settings
                val defaultSettings = mapOf(
                    "plans" to listOf(
                        mapOf("key" to "weekly", "label" to "Weekly", "price" to 5),
                        mapOf("key" to "monthly", "label" to "Monthly", "price" to 10),
                        mapOf("key" to "sixmonths", "label" to "Six Months", "price" to 50),
                        mapOf("key" to "yearly", "label" to "Yearly", "price" to 100)
                    ),
                    "subscriptionTiers" to listOf(
                        mapOf(
                            "key" to "basic",
                            "label" to "Basic",
                            "price" to 10,
                            "contactOnly" to false,
                            "features" to listOf("Limited tests/month", "Basic analytics")
                        ),
                        mapOf(
                            "key" to "pro",
                            "label" to "Pro",
                            "price" to 29,
                            "contactOnly" to false,
                            "features" to listOf(
                                "Unlimited tests",
                                "Advanced analytics",
                                "Branding/logo upload",
                                "Pass certificates (PDF)",
                                "Student reports"
                            )
                        ),
                        mapOf(
                            "key" to "institute",
                            "label" to "Institute Plan",
                            "price" to 0,
                            "contactOnly" to false,
                            "subtitle" to "For academies/schools",
                            "features" to listOf(
                                "Multiple instructors",
                                "Batch management",
                                "Attendance tracking",
                                "Institute dashboard"
                            )
                        )
                    ),
                    "accounts" to listOf(
                        mapOf("method" to "JazzCash", "number" to "0301-2345678", "type" to "Mobile Wallet", "name" to "Students Welfare Foundation"),
                        mapOf("method" to "Easypaisa", "number" to "0311-9876543", "type" to "Mobile Wallet", "name" to "Students Welfare Foundation"),
                        mapOf("method" to "Binance", "number" to "SWF2024", "type" to "Pay ID", "name" to "Students Welfare Foundation")
                    ),
                    "usdPkr" to 278.5,
                    "studentResultPriceUsd" to 5.0,
                    "examPortalEnabled" to true,
                    "examPortalStartHour" to 8,
                    "examPortalEndHour" to 20
                )
                Result.success(defaultSettings)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to load settings: ${e.message}"))
        }
    }

    /**
     * AI MCQ formatting via OpenRouter using key from platform_secrets/ai.
     */
    suspend fun formatMcqWithAi(prompt: String, modelId: String): Result<String> {
        return try {
            val doc = db.collection("platform_secrets").document("ai").get().await()
            val apiKey = doc.getString("openRouterApiKey")?.trim().orEmpty()
            if (apiKey.isBlank()) {
                return Result.failure(
                    Exception(
                        "AI is not set up. Super Admin → Settings → add OpenRouter API key. " +
                            "Or use Import / paste without AI."
                    )
                )
            }
            callOpenRouterDirect(apiKey, modelId, prompt)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to access AI settings: ${e.message}"))
        }
    }

    private suspend fun callOpenRouterDirect(
        apiKey: String,
        modelId: String,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("HTTP-Referer", "https://examapp-57718.web.app")
            conn.setRequestProperty("X-Title", "Exam System")
            conn.doOutput = true
            conn.doInput = true
            conn.connectTimeout = 45_000
            conn.readTimeout = 45_000

            val jsonPayload = JSONObject().apply {
                put("model", modelId)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            conn.outputStream.use { os ->
                val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseText)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val text = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    if (text.isNotBlank()) Result.success(text.trim())
                    else Result.failure(Exception("AI returned an empty response."))
                } else {
                    Result.failure(Exception("AI returned no choices."))
                }
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("AI error (${conn.responseCode}): $errorText"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Network error calling AI."))
        }
    }

    suspend fun submitPayment(payment: Payment): Result<Unit> {
        return try {
            val docRef = db.collection("payments").document()
            val newPayment = payment.copy(paymentId = docRef.id, createdAt = com.google.firebase.Timestamp.now())
            docRef.set(newPayment).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to submit payment: ${e.message}"))
        }
    }

    /**
     * Spark-plan friendly: compressed JPEG as a data-URL string in Firestore (no Storage / Blaze).
     * Stored in [User.brandingLogoUrl] / [Test.resultsLogoUrl]. Coil can display data: URLs.
     */
    suspend fun saveBrandingLogoToFirestore(
        context: android.content.Context,
        uri: android.net.Uri
    ): Result<String> {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (!mimeType.startsWith("image/")) {
                return Result.failure(Exception("Logo must be a PNG or JPEG image."))
            }
            val bytes = compressImageBytesFromUri(context, uri, maxDim = 400, jpegQuality = 65)
                ?: return Result.failure(Exception("Could not read the selected image."))
            if (bytes.size > 280_000) {
                return Result.failure(
                    Exception("Logo is still too large (${bytes.size / 1024} KB). Use a simpler or smaller image.")
                )
            }
            val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Result.success("data:image/jpeg;base64,$encoded")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save logo: ${e.message}"))
        }
    }

    /**
     * Requires Blaze + Storage enabled. Prefer [saveBrandingLogoToFirestore] on Spark.
     */
    suspend fun uploadBrandingLogoToStorage(
        context: android.content.Context,
        uri: android.net.Uri,
        instructorId: String
    ): Result<String> {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (!mimeType.startsWith("image/")) {
                return Result.failure(Exception("Logo must be a PNG or JPEG image."))
            }
            val bytes = compressImageBytesFromUri(context, uri, maxDim = 1024, jpegQuality = 80)
                ?: return Result.failure(Exception("Could not read the selected image."))
            val fileName = "logo_${System.currentTimeMillis()}.jpg"
            val path = "branding/$instructorId/$fileName"
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            val ref = storage.reference.child(path)
            ref.putBytes(bytes, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to upload logo: ${e.message}"))
        }
    }

    suspend fun updateInstructorBranding(
        uid: String,
        logoUrl: String? = null,
        resultsTitle: String? = null,
        conductedBy: String? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>()
            logoUrl?.let { updates["brandingLogoUrl"] = it }
            resultsTitle?.let { updates["brandingResultsTitle"] = it }
            conductedBy?.let { updates["brandingConductedBy"] = it }
            if (updates.isEmpty()) return Result.success(Unit)
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save branding: ${e.message}"))
        }
    }

    suspend fun updateTestResultsBranding(
        testId: String,
        logoUrl: String? = null,
        headerTitle: String? = null,
        conductedBy: String? = null
    ): Result<Unit> {
        return try {
            val docId = testId.trim().uppercase()
            if (docId.isBlank()) return Result.failure(Exception("Invalid test ID"))
            val updates = mutableMapOf<String, Any>()
            logoUrl?.let { updates["resultsLogoUrl"] = it }
            headerTitle?.let { updates["resultsHeaderTitle"] = it }
            conductedBy?.let { updates["resultsConductedBy"] = it }
            if (updates.isEmpty()) return Result.success(Unit)
            db.collection("tests").document(docId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save test branding: ${e.message}"))
        }
    }

    private suspend fun compressImageBytesFromUri(
        context: android.content.Context,
        uri: android.net.Uri,
        maxDim: Int = 1024,
        jpegQuality: Int = 80
    ): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val raw = inputStream.use { it.readBytes() }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@withContext null
            val width = bitmap.width
            val height = bitmap.height
            val scaled = if (width > maxDim || height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (width * ratio).toInt(),
                    (height * ratio).toInt(),
                    true
                )
            } else bitmap
            val outputStream = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 95), outputStream)
            outputStream.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadPaymentReceipt(context: android.content.Context, uri: android.net.Uri, mimeType: String = "image/jpeg"): Result<String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Unable to open selected file."))
            
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                inputStream.use { it.readBytes() }
            }
            
            val base64String = if (mimeType.startsWith("image")) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    val maxDim = 1024
                    val width = bitmap.width
                    val height = bitmap.height
                    val scaledBitmap = if (width > maxDim || height > maxDim) {
                        val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                        android.graphics.Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
                    } else {
                        bitmap
                    }
                    val outputStream = java.io.ByteArrayOutputStream()
                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val compressedBytes = outputStream.toByteArray()
                    val encoded = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                    "data:image/jpeg;base64,$encoded"
                } else {
                    val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    "data:$mimeType;base64,$encoded"
                }
            } else {
                val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                "data:$mimeType;base64,$encoded"
            }
            
            Result.success(base64String)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to process receipt: ${e.message}"))
        }
    }

    // ─── INSTITUTE (academies / schools) ───────────────────────────────────────

    private suspend fun loadInstituteDoc(instituteId: String): Institute? {
        if (instituteId.isBlank()) return null
        val instituteDoc = db.collection("institutes").document(instituteId).get().await()
        if (!instituteDoc.exists()) return null
        return instituteDoc.toObject(Institute::class.java)?.copy(instituteId = instituteDoc.id)
    }

    suspend fun getInstituteForUser(uid: String): Result<Institute?> {
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            val userInstituteId = userSnap.getString("instituteId")?.trim().orEmpty()
            if (userInstituteId.isNotBlank()) {
                loadInstituteDoc(userInstituteId)?.let { return Result.success(it) }
            }

            val owned = db.collection("institutes")
                .whereEqualTo("ownerUid", uid)
                .limit(1)
                .get()
                .await()
            if (!owned.isEmpty) {
                val doc = owned.documents.first()
                val institute = doc.toObject(Institute::class.java)?.copy(instituteId = doc.id)
                if (institute != null) return Result.success(institute)
            }

            try {
                val memberSnap = db.collectionGroup("members")
                    .whereEqualTo("uid", uid)
                    .limit(1)
                    .get()
                    .await()
                if (!memberSnap.isEmpty) {
                    val memberDoc = memberSnap.documents.first()
                    val instituteId = memberDoc.reference.parent.parent?.id
                    if (!instituteId.isNullOrBlank()) {
                        loadInstituteDoc(instituteId)?.let { return Result.success(it) }
                    }
                }
            } catch (_: Exception) {
                // Collection-group query may need an index; fall through to provisioning.
            }

            val tier = com.examsystem.app.data.InstructorTier.normalizeTierKey(
                userSnap.getString("subscriptionTier")
            )
            if (tier == com.examsystem.app.data.InstructorTier.INSTITUTE.key &&
                userSnap.getString("approvalStatus") == "approved" &&
                userSnap.getString("subscriptionStatus") == "active"
            ) {
                return ensureInstituteForOwner(uid)
            }

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to load institute: ${e.message}"))
        }
    }

    /** Creates institute + owner member when admin approved Institute tier but setup was incomplete. */
    suspend fun ensureInstituteForOwner(uid: String): Result<Institute> {
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            if (!userSnap.exists()) {
                return Result.failure(Exception("User profile not found."))
            }
            val name = userSnap.getString("name") ?: ""
            val email = (userSnap.getString("email") ?: "").trim().lowercase()
            val tier = com.examsystem.app.data.InstructorTier.normalizeTierKey(
                userSnap.getString("subscriptionTier")
            )
            if (tier != com.examsystem.app.data.InstructorTier.INSTITUTE.key) {
                return Result.failure(Exception("Institute plan is required to set up your academy."))
            }

            var instituteId = userSnap.getString("instituteId")?.trim().orEmpty()
            var institute = if (instituteId.isNotBlank()) loadInstituteDoc(instituteId) else null

            if (institute == null) {
                val instituteRef = db.collection("institutes").document()
                instituteId = instituteRef.id
                val instituteName = "${name.ifBlank { "Academy" }} Institute"
                instituteRef.set(
                    mapOf(
                        "instituteId" to instituteId,
                        "name" to instituteName,
                        "ownerUid" to uid,
                        "ownerEmail" to email,
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
                institute = Institute(
                    instituteId = instituteId,
                    name = instituteName,
                    ownerUid = uid,
                    ownerEmail = email
                )
            } else {
                instituteId = institute.instituteId
            }

            val memberRef = db.collection("institutes").document(instituteId)
                .collection("members").document(uid)
            if (!memberRef.get().await().exists()) {
                memberRef.set(
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "name" to name,
                        "role" to "owner",
                        "status" to "active",
                        "addedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
            }

            db.collection("users").document(uid).update(
                mapOf(
                    "instituteId" to instituteId,
                    "instituteRole" to "owner"
                )
            ).await()

            Result.success(institute)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to set up institute: ${e.message}"))
        }
    }

    suspend fun getInstituteMemberRole(instituteId: String, uid: String): String {
        return try {
            val doc = db.collection("institutes").document(instituteId)
                .collection("members").document(uid).get().await()
            val role = doc.getString("role")?.trim().orEmpty()
            if (role.isNotBlank()) return role
            val instituteDoc = db.collection("institutes").document(instituteId).get().await()
            if (instituteDoc.getString("ownerUid") == uid) "owner" else ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun getInstituteDashboardStats(instituteId: String): Result<Map<String, Int>> {
        return try {
            val members = db.collection("institutes").document(instituteId)
                .collection("members").get().await().size()
            val batchesSnap = db.collection("institutes").document(instituteId)
                .collection("batches").get().await()
            var students = 0
            batchesSnap.documents.forEach { batchDoc ->
                val count = db.collection("institutes").document(instituteId)
                    .collection("batches").document(batchDoc.id)
                    .collection("students").get().await().size()
                students += count
            }
            Result.success(
                mapOf(
                    "instructors" to members,
                    "batches" to batchesSnap.size(),
                    "students" to students
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load stats"))
        }
    }

    suspend fun getInstituteMembers(instituteId: String): Result<List<InstituteMember>> {
        return try {
            val snap = db.collection("institutes").document(instituteId)
                .collection("members").get().await()
            val list = snap.documents.mapNotNull { doc ->
                doc.toObject(InstituteMember::class.java)?.copy(uid = doc.id)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load members"))
        }
    }

    suspend fun addInstituteMemberByEmail(instituteId: String, email: String): Result<Unit> {
        return try {
            val cleanEmail = email.filter { !it.isWhitespace() }.trim().lowercase()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                return Result.failure(Exception("Enter a valid instructor email address."))
            }
            val userSnap = db.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .await()
            if (userSnap.isEmpty) {
                return Result.failure(Exception(
                    "No account with \"$cleanEmail\". They must open the app → Instructor → Sign Up first, then you can add them."
                ))
            }
            val userDoc = userSnap.documents.first()
            val user = userDoc.toObject(User::class.java)
                ?: return Result.failure(Exception("Invalid user record"))
            if (user.role != "instructor") {
                return Result.failure(Exception("That account is not an instructor."))
            }
            if (user.approvalStatus != "approved" || user.subscriptionStatus != "active") {
                return Result.failure(Exception(
                    "This instructor is still pending. Super Admin must approve them under Instructors (and Payments if needed) before you can add them here."
                ))
            }
            val linkedInstituteId = user.instituteId.trim()
            if (linkedInstituteId == instituteId) {
                return Result.failure(Exception("This instructor is already in your academy."))
            }
            if (linkedInstituteId.isNotBlank() && linkedInstituteId != instituteId) {
                return Result.failure(Exception("This instructor already belongs to another institute."))
            }
            val instituteDoc = db.collection("institutes").document(instituteId).get().await()
            if (!instituteDoc.exists()) {
                return Result.failure(Exception("Academy not found. Open Institute Dashboard and try again."))
            }
            if (userDoc.id == instituteDoc.getString("ownerUid")) {
                return Result.failure(Exception("This person is already the academy owner."))
            }
            val member = InstituteMember(
                uid = userDoc.id,
                email = user.email.ifBlank { cleanEmail },
                name = user.name,
                role = "instructor",
                status = "active",
                addedAt = Timestamp.now()
            )
            db.collection("institutes").document(instituteId)
                .collection("members").document(userDoc.id)
                .set(member)
                .await()
            db.collection("users").document(userDoc.id).update(
                mapOf(
                    "instituteId" to instituteId,
                    "instituteRole" to "instructor"
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message ?: "Failed to add instructor"
            val friendly = when {
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Permission denied. Make sure you are the academy owner and your account is approved."
                else -> msg
            }
            Result.failure(Exception(friendly))
        }
    }

    /**
     * Owner-creates an instructor account directly by supplying name, email, and password.
     * Uses a secondary [FirebaseApp] instance so the owner's session is NEVER disturbed.
     * The new account is immediately approved/active — no Super-Admin step needed.
     */
    suspend fun addInstituteMemberWithCredentials(
        instituteId: String,
        name: String,
        email: String,
        password: String
    ): Result<Unit> {
        return try {
            val cleanEmail = email.filter { !it.isWhitespace() }.trim().lowercase()
            val cleanName  = name.trim()

            if (cleanName.isBlank()) return Result.failure(Exception("Please enter the instructor's name."))
            if (cleanEmail.isBlank() || !cleanEmail.contains("@"))
                return Result.failure(Exception("Enter a valid email address."))
            if (password.length < 6) return Result.failure(Exception("Password must be at least 6 characters."))

            // Verify institute exists
            val instituteDoc = db.collection("institutes").document(instituteId).get().await()
            if (!instituteDoc.exists()) return Result.failure(Exception("Academy not found. Open Institute Dashboard and try again."))

            // Check if this email already has a Firestore user account
            val existingSnap = db.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get().await()

            val newUid: String

            if (existingSnap.isEmpty) {
                // ── Create a new Firebase Auth account via a secondary FirebaseApp ─────
                // This avoids signing out the currently logged-in owner.
                val secondaryAppName = "secondary_${System.currentTimeMillis()}"
                val primaryOptions = com.google.firebase.FirebaseApp.getInstance().options
                val secondaryOptions = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId(primaryOptions.applicationId)
                    .setApiKey(primaryOptions.apiKey ?: "")
                    .setProjectId(primaryOptions.projectId ?: "")
                    .setGcmSenderId(primaryOptions.gcmSenderId)
                    .setStorageBucket(primaryOptions.storageBucket)
                    .setDatabaseUrl(primaryOptions.databaseUrl)
                    .build()

                val secondaryApp = try {
                    com.google.firebase.FirebaseApp.initializeApp(
                        com.google.firebase.FirebaseApp.getInstance().applicationContext,
                        secondaryOptions,
                        secondaryAppName
                    )
                } catch (e: Exception) {
                    // App may already exist if name collision (extremely rare) — delete & retry
                    com.google.firebase.FirebaseApp.getInstance(secondaryAppName)
                }

                val secondaryAuth = com.google.firebase.auth.FirebaseAuth.getInstance(secondaryApp)
                val authResult = try {
                    secondaryAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                    secondaryApp.delete()
                    return Result.failure(Exception("An account with this email already exists. Use 'Add by email' instead, or choose a different email."))
                } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
                    secondaryApp.delete()
                    return Result.failure(Exception("Password is too weak. Use at least 6 characters."))
                } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                    secondaryApp.delete()
                    return Result.failure(Exception("Invalid email address. Please check and try again."))
                }

                newUid = authResult.user?.uid ?: run {
                    secondaryApp.delete()
                    return Result.failure(Exception("Account creation failed. Please try again."))
                }

                // Sign out from secondary app and delete the secondary instance
                secondaryAuth.signOut()
                secondaryApp.delete()

                // Write the Firestore user document — immediately approved & active
                val newUser = User(
                    uid = newUid,
                    email = cleanEmail,
                    name = cleanName,
                    role = "instructor",
                    approvalStatus = "approved",
                    subscriptionStatus = "active",
                    subscriptionTier = "basic",
                    instituteId = instituteId,
                    instituteRole = "instructor",
                    createdAt = Timestamp.now()
                )
                db.collection("users").document(newUid).set(newUser).await()

            } else {
                // ── Email already registered — link the existing account ───────────────
                val existingDoc  = existingSnap.documents.first()
                val existingUser = existingDoc.toObject(User::class.java)
                    ?: return Result.failure(Exception("Invalid user record for that email."))

                if (existingUser.role != "instructor")
                    return Result.failure(Exception("That account is not an instructor account."))

                val linkedInstitute = existingUser.instituteId.trim()
                if (linkedInstitute == instituteId)
                    return Result.failure(Exception("This instructor is already in your academy."))
                if (linkedInstitute.isNotBlank())
                    return Result.failure(Exception("This instructor already belongs to another institute."))
                if (existingDoc.id == instituteDoc.getString("ownerUid"))
                    return Result.failure(Exception("This person is already the academy owner."))

                newUid = existingDoc.id
                // Ensure their account is active so they can sign in
                db.collection("users").document(newUid).update(
                    mapOf(
                        "approvalStatus"    to "approved",
                        "subscriptionStatus" to "active",
                        "instituteId"       to instituteId,
                        "instituteRole"     to "instructor"
                    )
                ).await()
            }

            // ── Add to institute members subcollection ────────────────────────────────
            val member = InstituteMember(
                uid     = newUid,
                email   = cleanEmail,
                name    = cleanName,
                role    = "instructor",
                status  = "active",
                addedAt = Timestamp.now()
            )
            db.collection("institutes").document(instituteId)
                .collection("members").document(newUid)
                .set(member).await()

            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message ?: "Failed to add instructor"
            val friendly = when {
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Permission denied. Make sure you are the academy owner and your account is approved."
                else -> msg
            }
            Result.failure(Exception(friendly))
        }
    }

    suspend fun removeInstituteMember(instituteId: String, memberUid: String, ownerUid: String): Result<Unit> {
        return try {
            if (memberUid == ownerUid) {
                return Result.failure(Exception("Cannot remove the institute owner."))
            }
            db.collection("institutes").document(instituteId)
                .collection("members").document(memberUid).delete().await()
            db.collection("users").document(memberUid).update(
                mapOf("instituteId" to "", "instituteRole" to "")
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to remove member"))
        }
    }

    suspend fun getBatches(instituteId: String): Result<List<Batch>> {
        return try {
            val snap = db.collection("institutes").document(instituteId)
                .collection("batches").get().await()
            val list = snap.documents.mapNotNull { doc ->
                doc.toObject(Batch::class.java)?.copy(batchId = doc.id)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load batches"))
        }
    }

    suspend fun createBatch(instituteId: String, name: String, description: String): Result<String> {
        return try {
            val ref = db.collection("institutes").document(instituteId)
                .collection("batches").document()
            val batch = Batch(
                batchId = ref.id,
                name = name.trim(),
                description = description.trim(),
                studentCount = 0,
                createdAt = Timestamp.now()
            )
            ref.set(batch).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to create batch"))
        }
    }

    suspend fun deleteBatch(instituteId: String, batchId: String): Result<Unit> {
        return try {
            val batchRef = db.collection("institutes").document(instituteId)
                .collection("batches").document(batchId)
            val students = batchRef.collection("students").get().await()
            students.documents.forEach { it.reference.delete().await() }
            val attendance = batchRef.collection("attendance").get().await()
            attendance.documents.forEach { it.reference.delete().await() }
            batchRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to delete batch"))
        }
    }

    suspend fun getBatchStudents(instituteId: String, batchId: String): Result<List<BatchStudent>> {
        return try {
            val snap = db.collection("institutes").document(instituteId)
                .collection("batches").document(batchId)
                .collection("students").get().await()
            val list = snap.documents.mapNotNull { doc ->
                doc.toObject(BatchStudent::class.java)?.copy(studentId = doc.id)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load students"))
        }
    }

    suspend fun addBatchStudent(instituteId: String, batchId: String, name: String, rollNumber: String): Result<Unit> {
        val result = findOrCreateBatchStudent(
            instituteId = instituteId,
            batchId = batchId,
            linkedTestId = "",
            name = name,
            rollNumber = rollNumber,
            fatherName = "",
            district = "",
            gender = "",
            deviceId = "",
            allowCreateWithoutTestLink = true
        )
        return if (result.isSuccess) Result.success(Unit)
        else Result.failure(result.exceptionOrNull() ?: Exception("Failed to add student"))
    }

    /**
     * Finds roster student by roll or deviceId, or creates one when exam is linked to a batch.
     * Returns batch student document id.
     */
    suspend fun findOrCreateBatchStudent(
        instituteId: String,
        batchId: String,
        linkedTestId: String,
        name: String,
        rollNumber: String,
        fatherName: String,
        district: String,
        gender: String,
        deviceId: String,
        allowCreateWithoutTestLink: Boolean = false
    ): Result<String> {
        return try {
            if (instituteId.isBlank() || batchId.isBlank()) {
                return Result.success("")
            }
            val studentsRef = db.collection("institutes").document(instituteId)
                .collection("batches").document(batchId)
                .collection("students")
            val cleanRoll = rollNumber.trim()
            val cleanName = name.trim()

            if (cleanRoll.isNotBlank()) {
                val byRoll = studentsRef.whereEqualTo("rollNumber", cleanRoll).limit(1).get().await()
                if (!byRoll.isEmpty) {
                    val doc = byRoll.documents.first()
                    mergeBatchStudentProfile(doc.id, instituteId, batchId, cleanName, cleanRoll, fatherName, district, gender, deviceId, linkedTestId)
                    return Result.success(doc.id)
                }
            }
            if (deviceId.isNotBlank()) {
                val byDevice = studentsRef.whereEqualTo("deviceId", deviceId).limit(1).get().await()
                if (!byDevice.isEmpty) {
                    val doc = byDevice.documents.first()
                    mergeBatchStudentProfile(doc.id, instituteId, batchId, cleanName, cleanRoll, fatherName, district, gender, deviceId, linkedTestId)
                    return Result.success(doc.id)
                }
            }
            if (!allowCreateWithoutTestLink && linkedTestId.isBlank()) {
                return Result.success("")
            }
            val ref = studentsRef.document()
            val payload = hashMapOf(
                "studentId" to ref.id,
                "name" to cleanName,
                "rollNumber" to cleanRoll,
                "fatherName" to fatherName.trim(),
                "district" to district.trim(),
                "gender" to gender.trim(),
                "deviceId" to deviceId
            )
            if (linkedTestId.isNotBlank()) {
                payload["linkedTestId"] = linkedTestId.trim().uppercase()
            }
            ref.set(payload).await()
            updateBatchStudentCount(instituteId, batchId)
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to register student for class"))
        }
    }

    private suspend fun mergeBatchStudentProfile(
        studentDocId: String,
        instituteId: String,
        batchId: String,
        name: String,
        rollNumber: String,
        fatherName: String,
        district: String,
        gender: String,
        deviceId: String,
        linkedTestId: String
    ) {
        val updates = mutableMapOf<String, Any>()
        if (name.isNotBlank()) updates["name"] = name
        if (rollNumber.isNotBlank()) updates["rollNumber"] = rollNumber
        if (fatherName.isNotBlank()) updates["fatherName"] = fatherName
        if (district.isNotBlank()) updates["district"] = district
        if (gender.isNotBlank()) updates["gender"] = gender
        if (deviceId.isNotBlank()) updates["deviceId"] = deviceId
        if (linkedTestId.isNotBlank()) updates["linkedTestId"] = linkedTestId.trim().uppercase()
        if (updates.isEmpty()) return
        db.collection("institutes").document(instituteId)
            .collection("batches").document(batchId)
            .collection("students").document(studentDocId)
            .update(updates).await()
    }

    private suspend fun updateBatchStudentCount(instituteId: String, batchId: String) {
        val count = db.collection("institutes").document(instituteId)
            .collection("batches").document(batchId)
            .collection("students").get().await().size()
        db.collection("institutes").document(instituteId)
            .collection("batches").document(batchId)
            .update("studentCount", count).await()
    }

    suspend fun saveAttendance(
        instituteId: String,
        batchId: String,
        date: String,
        records: Map<String, String>,
        markedBy: String
    ): Result<Unit> {
        return try {
            if (instituteId.isBlank() || batchId.isBlank() || date.isBlank()) {
                return Result.failure(Exception("Missing institute or batch. Go back and open the batch again."))
            }
            if (records.isEmpty()) {
                return Result.failure(Exception("Add at least one student before saving attendance."))
            }
            val payload = hashMapOf(
                "date" to date,
                "records" to HashMap(records),
                "markedBy" to markedBy.trim(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("institutes").document(instituteId)
                .collection("batches").document(batchId)
                .collection("attendance").document(date)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Permission denied. Make sure your Institute plan is approved and you are linked to this academy."
                msg.isNotBlank() -> msg
                else -> "Failed to save attendance"
            }
            Result.failure(Exception(friendly))
        }
    }

    private fun parseAttendanceDoc(
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): AttendanceSession? {
        if (!doc.exists()) return null
        val fromObject = doc.toObject(AttendanceSession::class.java)
        @Suppress("UNCHECKED_CAST")
        val rawRecords = doc.get("records") as? Map<String, Any?>
        val records = if (rawRecords != null) {
            rawRecords.mapValues { (_, v) -> v?.toString()?.lowercase()?.trim() ?: "present" }
        } else {
            fromObject?.records ?: emptyMap()
        }
        return AttendanceSession(
            date = doc.getString("date")?.takeIf { it.isNotBlank() } ?: doc.id,
            records = records,
            markedBy = doc.getString("markedBy") ?: fromObject?.markedBy ?: "",
            updatedAt = doc.getTimestamp("updatedAt") ?: fromObject?.updatedAt
        )
    }

    suspend fun getAttendance(instituteId: String, batchId: String, date: String): Result<AttendanceSession?> {
        return try {
            val doc = db.collection("institutes").document(instituteId)
                .collection("batches").document(batchId)
                .collection("attendance").document(date)
                .get()
                .await()
            Result.success(parseAttendanceDoc(doc))
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Permission denied. Open Institute Dashboard and ensure your academy is set up."
                else -> msg.ifBlank { "Failed to load attendance" }
            }
            Result.failure(Exception(friendly))
        }
    }

    suspend fun getAttendanceHistory(instituteId: String, batchId: String): Result<List<AttendanceSession>> {
        return try {
            val snap = db.collection("institutes").document(instituteId)
                .collection("batches").document(batchId)
                .collection("attendance")
                .get()
                .await()
            val list = snap.documents.mapNotNull { parseAttendanceDoc(it) }
                .sortedByDescending { it.date }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to load attendance history"))
        }
    }
}
