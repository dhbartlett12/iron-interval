package org.ironinterval.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class HoldService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Workout timer", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH)
            .setContentTitle("Iron Interval")
            .setContentText("Timer running")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(7, n)
        }
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ironinterval:hold").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        if (wake?.isHeld == true) wake?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CH = "iron_hold"
    }
}
