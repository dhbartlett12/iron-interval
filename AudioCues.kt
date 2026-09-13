package org.ironinterval.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AudioCues {
    fun tick() = tone(ToneGenerator.TONE_PROP_BEEP, 80)

    fun gong() = tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)

    fun doubleGong() {
        tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)
        try { Thread.sleep(220) } catch (_: InterruptedException) {}
        tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
    }

    fun bells(count: Int) {
        val n = count.coerceIn(1, 8)
        repeat(n) { i ->
            tone(ToneGenerator.TONE_PROP_BEEP2, 280)
            if (i < n - 1) try { Thread.sleep(420) } catch (_: InterruptedException) {}
        }
    }

    fun playKind(kind: String, count: Int) {
        when (kind) {
            "gong" -> gong()
            "double" -> doubleGong()
            "bells" -> bells(count)
            "tick" -> tick()
            else -> gong()
        }
    }

    private fun tone(tone: Int, ms: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tg.startTone(tone, ms)
            Thread.sleep(ms.toLong() + 30)
            tg.release()
        } catch (_: Exception) {}
    }

    fun vibrate(context: Context, ms: Long) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vib.hasVibrator()) return
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }
}
