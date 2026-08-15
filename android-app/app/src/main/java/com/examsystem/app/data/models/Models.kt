package com.examsystem.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude

// ─── Core Models ─────────────────────────────────────────────────────────────

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "",           // "superadmin" | "instructor"
    val approvalStatus: String = "pending", // "pending" | "approved" | "suspended"
    val subscriptionStatus: String = "inactive",
    /** basic | pro | institute — set when admin approves instructor payment */
    val subscriptionTier: String = "",
    /** Set for institute owners; members are linked via institutes/{id}/members */
    val instituteId: String = "",
    val instituteRole: String = "", // owner | instructor
    val createdAt: com.google.firebase.Timestamp? = null,
    /** HTTPS Storage URL or data:image/jpeg;base64,... (Spark-friendly) */
    @get:PropertyName("brandingLogoUrl") @set:PropertyName("brandingLogoUrl")
    var brandingLogoUrl: String = "",
    @get:PropertyName("brandingResultsTitle") @set:PropertyName("brandingResultsTitle")
    var brandingResultsTitle: String = "",
    @get:PropertyName("brandingConductedBy") @set:PropertyName("brandingConductedBy")
    var brandingConductedBy: String = ""
) {
    @get:Exclude
    val isSuperAdmin: Boolean get() = role == "superadmin"

    @get:Exclude
    val isApproved: Boolean get() = isSuperAdmin || approvalStatus == "approved"

    @get:Exclude
    val hasActiveSubscription: Boolean get() = isSuperAdmin || subscriptionStatus == "active"
}

data class Institute(
    val instituteId: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val createdAt: com.google.firebase.Timestamp? = null
)

data class InstituteMember(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "instructor", // owner | instructor
    val status: String = "active",
    val addedAt: com.google.firebase.Timestamp? = null
)

data class Batch(
    val batchId: String = "",
    val name: String = "",
    val description: String = "",
    val studentCount: Int = 0,
    val createdAt: com.google.firebase.Timestamp? = null
)

data class BatchStudent(
    val studentId: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val fatherName: String = "",
    val district: String = "",
    val gender: String = "",
    /** Last device that took an exam as this roster student (for skip re-entry). */
    val deviceId: String = ""
)

/** Copied to tests_public so students can pick their name without reading institute data. */
data class TestRosterStudent(
    val studentId: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val fatherName: String = "",
    val district: String = "",
    val gender: String = ""
)

/** Attendance for one batch on one date — records map studentId → present | absent | late */
data class AttendanceSession(
    val date: String = "",
    val records: Map<String, String> = emptyMap(),
    val markedBy: String = "",
    val updatedAt: com.google.firebase.Timestamp? = null
)

data class Section(
    val id: String = "",
    val title: String = "",
    val questions: List<Question> = emptyList()
)

data class Question(
    val id: String = "",
    val text: String = "",
    val imageUrl: String = "",       // URL for question diagram/image
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctAnswer: String = "",  // "A" | "B" | "C" | "D"
    val marks: Int = 1
) {
    fun toOptionsList() = listOf(optionA, optionB, optionC, optionD)
}

data class Test(
    val testId: String = "",
    val instructorId: String = "",
    val title: String = "",
    val instructions: String = "",
    val durationMinutes: Int = 60,
    val passingMarks: Int = 0,
    val totalMarks: Int = 0,
    @get:PropertyName("isEnabled") @set:PropertyName("isEnabled")
    var isEnabled: Boolean = true,
    val sections: List<Section> = emptyList(),
    val releaseScoreMode: String = "table_only",
    val resultReleaseTime: Timestamp? = null,
    @get:PropertyName("resultsReleasedEarly") @set:PropertyName("resultsReleasedEarly")
    var resultsReleasedEarly: Boolean = false,
    val createdAt: Timestamp? = null,
    // Anti-cheat (instructor toggles in Create Test)
    @get:PropertyName("antiCheatFullscreen") @set:PropertyName("antiCheatFullscreen")
    var antiCheatFullscreen: Boolean = true,
    @get:PropertyName("antiCheatDetectLeaveApp") @set:PropertyName("antiCheatDetectLeaveApp")
    var antiCheatDetectLeaveApp: Boolean = true,
    @get:PropertyName("antiCheatBlockCopyPaste") @set:PropertyName("antiCheatBlockCopyPaste")
    var antiCheatBlockCopyPaste: Boolean = true,
    @get:PropertyName("antiCheatBlockScreenshot") @set:PropertyName("antiCheatBlockScreenshot")
    var antiCheatBlockScreenshot: Boolean = true,
    @get:PropertyName("antiCheatCamera") @set:PropertyName("antiCheatCamera")
    var antiCheatCamera: Boolean = false,
    @get:PropertyName("antiCheatRandomizeQuestions") @set:PropertyName("antiCheatRandomizeQuestions")
    var antiCheatRandomizeQuestions: Boolean = true,
    @get:PropertyName("antiCheatRandomizeOptions") @set:PropertyName("antiCheatRandomizeOptions")
    var antiCheatRandomizeOptions: Boolean = true,
    @get:PropertyName("antiCheatAutoSubmit") @set:PropertyName("antiCheatAutoSubmit")
    var antiCheatAutoSubmit: Boolean = true,
    /** Per-exam results branding (HTTPS URL or data:image/jpeg;base64,...) */
    @get:PropertyName("resultsLogoUrl") @set:PropertyName("resultsLogoUrl")
    var resultsLogoUrl: String = "",
    @get:PropertyName("resultsHeaderTitle") @set:PropertyName("resultsHeaderTitle")
    var resultsHeaderTitle: String = "",
    @get:PropertyName("resultsConductedBy") @set:PropertyName("resultsConductedBy")
    var resultsConductedBy: String = "",
    /** When set, students pick from roster / auto-register for attendance. */
    val instituteId: String = "",
    val batchId: String = "",
    val roster: List<TestRosterStudent> = emptyList()
)

data class Attempt(
    val attemptId: String = "",
    val testId: String = "",
    val studentName: String = "",
    val fatherName: String = "",
    val district: String = "",
    val gender: String = "",
    val answers: Map<String, String> = emptyMap(),
    val sectionScores: Map<String, Int> = emptyMap(),
    val totalScore: Int = 0,
    val rank: Int = 0,
    val deviceId: String = "",
    val ipAddress: String = "",
    val submittedAt: Timestamp? = null,
    val cheatAlerts: Int = 0,
    val cheatEvents: List<String> = emptyList(), // e.g. "LEFT_APP", "SPLIT_SCREEN"
    /** questionId → A/B/C/D — written when payment is approved so review can show correct options */
    val correctAnswers: Map<String, String> = emptyMap(),
    @get:PropertyName("hasPaidForDetails") @set:PropertyName("hasPaidForDetails")
    var hasPaidForDetails: Boolean = false,
    /** Links to institutes/{id}/batches/{id}/students/{studentId} for attendance. */
    val batchStudentId: String = ""
)

data class Payment(
    val paymentId: String = "",
    val paymentType: String = "instructor_subscription", // "instructor_subscription" or "student_result"
    val testId: String? = null,
    val attemptId: String? = null,
    val userEmail: String = "",
    val studentName: String? = null, // only for student_result
    val plan: String = "",           // "weekly", "monthly", "student_result" etc
    val screenshotUrl: String = "",
    val referenceNumber: String = "",
    val status: String = "pending",
    val createdAt: Timestamp? = null
)

data class Subscription(
    val instructorId: String = "",
    val plan: String = "",
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = false
)
