package com.examsystem.app.util

import com.examsystem.app.data.models.Test
import com.google.firebase.Timestamp

object ResultsRelease {
    private val pktFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi") }

    fun releaseTimeMillis(test: Test): Long? {
        val ts = test.resultReleaseTime ?: return null
        return ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
    }

    fun formattedReleaseTime(test: Test): String? {
        val ms = releaseTimeMillis(test) ?: return null
        return pktFormat.format(java.util.Date(ms)) + " (PKT)"
    }

    fun isReleased(test: Test): Boolean {
        if (test.resultsReleasedEarly) return true
        val releaseMs = releaseTimeMillis(test) ?: return true
        return System.currentTimeMillis() >= releaseMs
    }

    fun lockedMessage(test: Test): String {
        if (test.resultsReleasedEarly) return "Results are not yet available."
        val whenStr = formattedReleaseTime(test)
        return if (whenStr != null) {
            "Results will be published at:\n$whenStr"
        } else {
            "Results are not yet available. Please check back later."
        }
    }

    /** Human-readable countdown, or null if already released / no schedule. */
    fun countdownLabel(test: Test): String? {
        if (isReleased(test)) return null
        val releaseMs = releaseTimeMillis(test) ?: return null
        var diff = releaseMs - System.currentTimeMillis()
        if (diff <= 0L) return null
        val days = diff / (24 * 60 * 60 * 1000L)
        diff %= 24 * 60 * 60 * 1000L
        val hours = diff / (60 * 60 * 1000L)
        diff %= 60 * 60 * 1000L
        val minutes = diff / (60 * 1000L)
        return when {
            days > 0 -> "About ${days}d ${hours}h until results"
            hours > 0 -> "About ${hours}h ${minutes}m until results"
            minutes > 0 -> "About ${minutes} min until results"
            else -> "Results releasing very soon"
        }
    }
}
