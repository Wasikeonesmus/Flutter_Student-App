package com.examsystem.app.util

import kotlin.math.roundToInt

object PlatformPricing {
    const val DEFAULT_STUDENT_RESULT_USD = 5.0
    const val DEFAULT_USD_PKR = 278.5

    fun usdPkrRate(settings: Map<String, Any>): Double =
        (settings["usdPkr"] as? Number)?.toDouble() ?: DEFAULT_USD_PKR

    fun studentResultPriceUsd(settings: Map<String, Any>): Double {
        (settings["studentResultPriceUsd"] as? Number)?.toDouble()?.let { return it }
        @Suppress("UNCHECKED_CAST")
        val plans = settings["plans"] as? List<Map<String, Any>> ?: return DEFAULT_STUDENT_RESULT_USD
        for (plan in plans) {
            if ((plan["key"] as? String)?.lowercase() == "student_result") {
                return (plan["price"] as? Number)?.toDouble() ?: DEFAULT_STUDENT_RESULT_USD
            }
        }
        return DEFAULT_STUDENT_RESULT_USD
    }

    fun studentResultPricePkr(settings: Map<String, Any>): Int =
        (studentResultPriceUsd(settings) * usdPkrRate(settings)).roundToInt()

    fun studentResultPriceLabel(settings: Map<String, Any>): String =
        "Rs. ${studentResultPricePkr(settings)}"
}
