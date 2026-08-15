package com.examsystem.app.data

/**
 * Instructor subscription tiers — synced from Firestore platform_settings.subscriptionTiers.
 */
object SubscriptionPlans {
    fun defaultTiers(): List<Map<String, Any>> = listOf(
        mapOf(
            "key" to "basic",
            "label" to "Basic",
            "price" to 10,
            "contactOnly" to false,
            "features" to listOf(
                "Limited tests/month",
                "Basic analytics"
            )
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
            "contactOnly" to true,
            "subtitle" to "For academies/schools",
            "features" to listOf(
                "Multiple instructors",
                "Batch management",
                "Attendance tracking",
                "Institute dashboard"
            )
        )
    )

    fun resolveTiers(data: Map<String, Any>?): List<Map<String, Any>> {
        @Suppress("UNCHECKED_CAST")
        val fromServer = data?.get("subscriptionTiers") as? List<Map<String, Any>>
        return if (!fromServer.isNullOrEmpty()) fromServer else defaultTiers()
    }
}
