# Iron Interval — native Android

This is not Godot. It is a real Android app.

- Exact alarms (`AlarmManager.setExactAndAllowWhileIdle`) fire each gong/bell even if the screen is off
- A foreground media service holds a partial wake lock while PLAY is running
- Tones use `STREAM_ALARM` (the alarm-clock speaker, not media)

## Open and install

1. Install Android Studio (or the SDK you already have).
2. File → Open → this `iron_interval_android` folder.
3. Wait for Gradle sync.
4. Plug in the phone (or use the one you already use). USB debugging on.
5. Run (green triangle). Or Build → Build APK(s).

First launch will ask for notifications, exact alarms, and battery exemption. Allow all three.

Then: Sound → **Alarm volume** up. DND → allow Alarms.

PLAY. Lock the phone. Wait for the first gong.

## Session

PLAY → 5s ticks → gong → rest × N
1 bell, 2 bells, 3… after each rest
Last rest → double gong → change period → stop
