package org.ironinterval.app

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var sets = 4
    private var restSec = 90
    private var changeSec = 120
    private val delaySec = 5

    private var running = false
    private var t0Elapsed = 0L
    private var events = listOf<Event>()
    private var fired = hashSetOf<Int>()
    private var sliceStart = 0f
    private var sliceLen = 0f
    private var phase = PHASE_IDLE
    private var setI = 0
    private var lastTickWhole = -1

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (running) {
                sync()
                handler.postDelayed(this, 200)
            }
        }
    }

    private lateinit var clock: TextView
    private lateinit var phaseV: TextView
    private lateinit var setLine: TextView
    private lateinit var status: TextView
    private lateinit var setsVal: TextView
    private lateinit var restVal: TextView
    private lateinit var changeVal: TextView
    private lateinit var play: Button

    data class Event(val t: Float, val kind: String, val count: Int, val set: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        load()
        clock = findViewById(R.id.clock)
        phaseV = findViewById(R.id.phase)
        setLine = findViewById(R.id.setLine)
        status = findViewById(R.id.status)
        setsVal = findViewById(R.id.setsVal)
        restVal = findViewById(R.id.restVal)
        changeVal = findViewById(R.id.changeVal)
        play = findViewById(R.id.play)

        findViewById<Button>(R.id.setsMinus).setOnClickListener { nudgeSets(-1) }
        findViewById<Button>(R.id.setsPlus).setOnClickListener { nudgeSets(1) }
        findViewById<Button>(R.id.restMinus).setOnClickListener { nudgeRest(-5) }
        findViewById<Button>(R.id.restPlus).setOnClickListener { nudgeRest(5) }
        findViewById<Button>(R.id.changeMinus).setOnClickListener { nudgeChange(-5) }
        findViewById<Button>(R.id.changePlus).setOnClickListener { nudgeChange(5) }
        play.setOnClickListener { if (running) stop() else start() }
        findViewById<Button>(R.id.test).setOnClickListener {
            Thread { AudioCues.gong(); AudioCues.bells(2) }.start()
        }
        paintIdle()
        askPerms()
    }

    private fun askPerms() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
        }
    }

    private fun nudgeSets(d: Int) {
        if (running) return
        sets = (sets + d).coerceIn(1, 30)
        save(); paintIdle()
    }

    private fun nudgeRest(d: Int) {
        if (running) return
        restSec = (restSec + d).coerceIn(5, 600)
        save(); paintIdle()
    }

    private fun nudgeChange(d: Int) {
        if (running) return
        changeSec = (changeSec + d).coerceIn(5, 900)
        save(); paintIdle()
    }

    private fun start() {
        events = buildEvents()
        fired.clear()
        t0Elapsed = SystemClock.elapsedRealtime()
        setI = 1
        running = true
        phase = PHASE_COUNT
        sliceStart = 0f
        sliceLen = delaySec.toFloat()
        lastTickWhole = -1
        play.text = "STOP"
        play.setBackgroundResource(R.drawable.btn_stop)
        startForegroundService(Intent(this, HoldService::class.java))
        armAlarms()
        paintLabels()
        handler.removeCallbacks(tick)
        handler.post(tick)
        sync()
    }

    private fun stop() {
        running = false
        phase = PHASE_IDLE
        setI = 0
        handler.removeCallbacks(tick)
        cancelAlarms()
        stopService(Intent(this, HoldService::class.java))
        play.text = "PLAY"
        play.setBackgroundResource(R.drawable.btn_go)
        paintIdle()
    }

    private fun buildEvents(): List<Event> {
        val out = ArrayList<Event>()
        var t = delaySec.toFloat()
        out.add(Event(t, "gong", 1, 1))
        for (i in 0 until sets) {
            t += restSec
            if (i < sets - 1) out.add(Event(t, "bells", i + 1, i + 2))
            else out.add(Event(t, "double", 2, sets))
        }
        t += changeSec
        out.add(Event(t, "done", 0, sets))
        return out
    }

    private fun sync() {
        if (!running) return
        val now = (SystemClock.elapsedRealtime() - t0Elapsed) / 1000f
        events.forEachIndexed { i, ev ->
            if (i in fired) return@forEachIndexed
            if (now + 0.02f >= ev.t) {
                fired.add(i)
                fire(ev)
            }
        }
        if (!running) return
        var end = sliceStart + sliceLen
        if (phase == PHASE_COUNT) {
            end = delaySec.toFloat()
            sliceLen = delaySec.toFloat()
            sliceStart = 0f
        }
        val rem = (end - now).coerceAtLeast(0f)
        clock.text = fmt(kotlin.math.ceil(rem - 0.001).toInt().coerceAtLeast(0))
        val whole = kotlin.math.ceil(rem - 0.001).toInt()
        if (phase == PHASE_COUNT && whole != lastTickWhole && whole > 0) {
            lastTickWhole = whole
            Thread { AudioCues.tick() }.start()
        }
    }

    private fun fire(ev: Event) {
        when (ev.kind) {
            "gong" -> {
                Thread { AudioCues.gong(); AudioCues.vibrate(this, 280) }.start()
                setI = ev.set
                phase = PHASE_SET
                sliceLen = restSec.toFloat()
                sliceStart = ev.t
            }
            "bells" -> {
                Thread { AudioCues.bells(ev.count); AudioCues.vibrate(this, 220) }.start()
                setI = ev.set
                phase = PHASE_SET
                sliceLen = restSec.toFloat()
                sliceStart = ev.t
            }
            "double" -> {
                Thread { AudioCues.doubleGong(); AudioCues.vibrate(this, 400) }.start()
                setI = ev.set
                phase = PHASE_CHANGE
                sliceLen = changeSec.toFloat()
                sliceStart = ev.t
            }
            "done" -> {
                running = false
                phase = PHASE_DONE
                handler.removeCallbacks(tick)
                cancelAlarms()
                stopService(Intent(this, HoldService::class.java))
                play.text = "PLAY"
                play.setBackgroundResource(R.drawable.btn_go)
                clock.text = "0:00"
            }
        }
        paintLabels()
    }

    private fun armAlarms() {
        val am = getSystemService(AlarmManager::class.java)
        val wall0 = System.currentTimeMillis()
        events.forEachIndexed { i, ev ->
            if (ev.kind == "done") return@forEachIndexed
            val whenMs = wall0 + (ev.t * 1000).toLong()
            val pi = pending(i, ev)
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
        }
    }

    private fun cancelAlarms() {
        val am = getSystemService(AlarmManager::class.java)
        for (i in 0 until 40) {
            am.cancel(pending(i, Event(0f, "gong", 1, 1)))
        }
    }

    private fun pending(id: Int, ev: Event): PendingIntent {
        val i = Intent(this, CueReceiver::class.java).apply {
            action = CueReceiver.ACTION
            putExtra(CueReceiver.EXTRA_KIND, ev.kind)
            putExtra(CueReceiver.EXTRA_COUNT, ev.count)
        }
        return PendingIntent.getBroadcast(
            this, 200 + id, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun paintIdle() {
        setsVal.text = sets.toString()
        restVal.text = fmt(restSec)
        changeVal.text = fmt(changeSec)
        phaseV.text = "READY"
        setLine.text = "$sets sets  ·  ${fmt(restSec)} rest  ·  ${fmt(changeSec)} change"
        clock.text = fmt(delaySec)
        status.text = "Play. The clock runs itself. Lock the phone."
    }

    private fun paintLabels() {
        setsVal.text = sets.toString()
        restVal.text = fmt(restSec)
        changeVal.text = fmt(changeSec)
        when (phase) {
            PHASE_COUNT -> {
                phaseV.text = "COUNTDOWN"
                setLine.text = "Set 1 of $sets"
                status.text = "Gong starts set 1."
            }
            PHASE_SET -> {
                phaseV.text = "SET"
                setLine.text = "Set $setI of $sets"
                status.text = if (setI >= sets) "Last set. Double gong at zero." else "Bell at zero."
            }
            PHASE_CHANGE -> {
                phaseV.text = "CHANGE"
                setLine.text = "Next exercise"
                status.text = "Walk. Change plates."
            }
            PHASE_DONE -> {
                phaseV.text = "DONE"
                setLine.text = "Next exercise"
                status.text = "Press PLAY for the next lift."
            }
        }
    }

    private fun fmt(s: Int) = "%d:%02d".format(s / 60, s % 60)

    private fun prefs() = getSharedPreferences("iron", MODE_PRIVATE)
    private fun load() {
        val p = prefs()
        sets = p.getInt("sets", 4)
        restSec = p.getInt("rest", 90)
        changeSec = p.getInt("change", 120)
    }
    private fun save() {
        prefs().edit()
            .putInt("sets", sets)
            .putInt("rest", restSec)
            .putInt("change", changeSec)
            .apply()
    }

    companion object {
        const val PHASE_IDLE = 0
        const val PHASE_COUNT = 1
        const val PHASE_SET = 2
        const val PHASE_CHANGE = 3
        const val PHASE_DONE = 4
    }
}
