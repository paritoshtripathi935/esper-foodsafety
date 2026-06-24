package com.esper.foodsafety.ui.screens

import android.app.ActivityManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * ANDROID-14: Kiosk lock-task / screen-pinning.
 *
 * startLockTask() works without device-owner for Android screen pinning.
 * The user can exit via the System UI pin-exit gesture (hold Back+Recent simultaneously),
 * which is acceptable for a demo safety net.
 *
 * For full Esper lock-task (no exit possible without device owner):
 *   1. Enroll device in Esper tenant.
 *   2. Deploy app via Blueprint with kiosk-mode Blueprint.
 *   3. The device-owner DPC calls startLockTask() so the system UI escape is blocked.
 *
 * To disable screen pinning for dev: swipe up + hold, or run:
 *   adb shell am task lock stop
 */
open class KioskActivity : ComponentActivity() {

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startLockTask()
        Log.d("KioskActivity", "Lock-task started (screen pinning)")
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SafeTemp::KioskWakeLock"
        )
        wakeLock?.acquire()
    }

    override fun onDestroy() {
        wakeLock?.release()
        wakeLock = null
        super.onDestroy()
        try {
            stopLockTask()
        } catch (_: Exception) {
        }
    }

    fun isInLockTask(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}
