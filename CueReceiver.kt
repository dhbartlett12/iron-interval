package org.ironinterval.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "gong"
        val count = intent.getIntExtra(EXTRA_COUNT, 1)
        val pending = goAsync()
        Thread {
            try {
                AudioCues.playKind(kind, count)
                AudioCues.vibrate(context, if (kind == "double") 400 else 280)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_COUNT = "count"
        const val ACTION = "org.ironinterval.app.CUE"
    }
}
