package com.examsystem.app.data

import com.examsystem.app.data.models.Test
import com.examsystem.app.data.models.User
import java.util.Calendar

/** Product tiers: Basic, Pro, Institute — enforced in the Android instructor app. */
enum class InstructorTier(val key: String, val displayName: String) {
    BASIC("basic", "Basic"),
    PRO("pro", "Pro"),
    INSTITUTE("institute", "Institute Plan");

    companion object {
        /** Firestore / payment plan strings → canonical key: basic | pro | institute */
        fun normalizeTierKey(raw: String?): String {
            val p = raw?.lowercase()?.trim().orEmpty()
            if (p.isEmpty()) return BASIC.key
            if (p.contains("institute")) return INSTITUTE.key
            if (p == PRO.key || p.startsWith("pro ") || p == "professional") return PRO.key
            if (p == BASIC.key) return BASIC.key
            return BASIC.key
        }

        fun fromKey(raw: String?): InstructorTier = when (normalizeTierKey(raw)) {
            PRO.key -> PRO
            INSTITUTE.key -> INSTITUTE
            else -> BASIC
        }
    }
}

data class TierCapabilities(
    val tier: InstructorTier,
    /** null = unlimited */
    val maxTestsPerMonth: Int?,
    val basicAnalyticsOnly: Boolean,
    val brandingUpload: Boolean,
    val customCertificates: Boolean,
    val studentReports: Boolean,
    val cameraProctoring: Boolean,
    val instituteHub: Boolean
)

object SubscriptionCapabilities {
    /** Basic plan: limited tests per calendar month */
    const val BASIC_MONTHLY_TEST_LIMIT = 10

    fun forTier(tier: InstructorTier): TierCapabilities = when (tier) {
        InstructorTier.BASIC -> TierCapabilities(
            tier = tier,
            maxTestsPerMonth = BASIC_MONTHLY_TEST_LIMIT,
            basicAnalyticsOnly = true,
            brandingUpload = false,
            customCertificates = false,
            studentReports = false,
            cameraProctoring = false,
            instituteHub = false
        )
        InstructorTier.PRO -> TierCapabilities(
            tier = tier,
            maxTestsPerMonth = null,
            basicAnalyticsOnly = false,
            brandingUpload = true,
            customCertificates = true,
            studentReports = true,
            cameraProctoring = true,
            instituteHub = false
        )
        InstructorTier.INSTITUTE -> TierCapabilities(
            tier = tier,
            maxTestsPerMonth = null,
            basicAnalyticsOnly = false,
            brandingUpload = true,
            customCertificates = true,
            studentReports = true,
            cameraProctoring = true,
            instituteHub = true
        )
    }

    fun fromUser(user: User?): TierCapabilities {
        if (user?.isSuperAdmin == true) return forTier(InstructorTier.INSTITUTE)
        return forTier(InstructorTier.fromKey(user?.subscriptionTier))
    }

    fun countTestsCreatedThisMonth(tests: List<Test>): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        return tests.count { test ->
            val created = test.createdAt?.toDate()?.time ?: return@count false
            created >= monthStart
        }
    }

    fun canCreateNewTest(user: User?, tests: List<Test>, isEditingExisting: Boolean): Pair<Boolean, String?> {
        if (isEditingExisting) return true to null
        if (user?.isSuperAdmin == true) return true to null
        val cap = fromUser(user)
        val limit = cap.maxTestsPerMonth ?: return true to null
        val used = countTestsCreatedThisMonth(tests)
        if (used >= limit) {
            return false to "Basic plan allows $limit tests per month ($used used). Upgrade to Pro for unlimited tests."
        }
        return true to null
    }
}
