package com.examsystem.app.util

import java.util.Calendar
import java.util.TimeZone

/** Exam portal hours are enforced in Pakistan Standard Time (PKT, UTC+5). */
object ExamSchedule {
    private val PKT = TimeZone.getTimeZone("Asia/Karachi")

    data class PortalConfig(
        val enabled: Boolean = true,
        val startHourPkt: Int = 8,
        val endHourPktExclusive: Int = 20
    )

    @Volatile
    private var portalConfig = PortalConfig()

    fun applyPlatformSettings(settings: Map<String, Any>) {
        portalConfig = PortalConfig(
            enabled = settings["examPortalEnabled"] as? Boolean ?: true,
            startHourPkt = ((settings["examPortalStartHour"] as? Number)?.toInt() ?: 8).coerceIn(0, 23),
            endHourPktExclusive = ((settings["examPortalEndHour"] as? Number)?.toInt() ?: 20).coerceIn(1, 24)
        )
    }

    fun currentHourPkt(): Int =
        Calendar.getInstance(PKT).get(Calendar.HOUR_OF_DAY)

    fun isPortalOpenPkt(): Boolean {
        // Portal is always open — no time restriction
        return true
    }

    fun portalClosedMessage(): String {
        val cfg = portalConfig
        return "The exam portal is closed. Exams are only available between " +
            "${cfg.startHourPkt}:00 and ${cfg.endHourPktExclusive}:00 (Pakistan time)."
    }
}
