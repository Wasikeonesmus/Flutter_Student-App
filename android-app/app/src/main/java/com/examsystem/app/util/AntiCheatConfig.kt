package com.examsystem.app.util

import com.examsystem.app.data.models.Test

/** Per-exam anti-cheat settings (stored on [Test] in Firestore). */
data class AntiCheatConfig(
    val fullscreen: Boolean = true,
    val detectLeaveApp: Boolean = true,
    val blockCopyPaste: Boolean = true,
    val blockScreenshot: Boolean = true,
    val cameraMonitoring: Boolean = false,
    val randomizeQuestions: Boolean = true,
    val randomizeOptions: Boolean = true,
    val autoSubmitOnTimeout: Boolean = true
) {
    companion object {
        fun fromTest(test: Test?) = AntiCheatConfig(
            fullscreen = test?.antiCheatFullscreen ?: true,
            detectLeaveApp = test?.antiCheatDetectLeaveApp ?: true,
            blockCopyPaste = test?.antiCheatBlockCopyPaste ?: true,
            blockScreenshot = test?.antiCheatBlockScreenshot ?: true,
            cameraMonitoring = test?.antiCheatCamera ?: false,
            randomizeQuestions = test?.antiCheatRandomizeQuestions ?: true,
            randomizeOptions = test?.antiCheatRandomizeOptions ?: true,
            autoSubmitOnTimeout = test?.antiCheatAutoSubmit ?: true
        )
    }
}

enum class AntiCheatViolation(val label: String) {
    LEFT_APP("Left exam / switched app"),
    SPLIT_SCREEN("Split-screen or multi-window detected"),
    SCREENSHOT_BLOCKED("Screenshot attempt blocked"),
    CAMERA_OFF("Camera monitoring interrupted")
}
