package com.esper.foodsafety.alerts

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager

class AlertSoundPlayer(private val context: Context) {

    private var ringtone: Ringtone? = null

    fun startAlarm() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (_: Exception) {
        }
    }

    fun stopAlarm() {
        ringtone?.stop()
        ringtone = null
    }
}
