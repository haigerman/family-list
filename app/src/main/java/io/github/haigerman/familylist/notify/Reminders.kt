package io.github.haigerman.familylist.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.haigerman.familylist.MainActivity
import io.github.haigerman.familylist.R
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Settings
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.Task
import io.github.haigerman.familylist.data.TaskDone
import java.time.ZoneId

/**
 * Erinnerungen: Für jeden offenen Task mit Erinnerungszeit, der einen selbst betrifft, stellt die
 * App einen Wecker im Gerät; zur Zeit erscheint die Benachrichtigung. Tasks der anderen kommen
 * über den Abgleich (auch im Hintergrund) aufs Gerät.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_ID = "id"
    /** Was der Empfänger tun soll: fehlt die Angabe, ist es der Wecker eines Tasks. */
    const val EXTRA_KIND = "kind"
    const val KIND_DONE = "done"
    const val KIND_TEST_CLOCK = "test-clock"
    const val KIND_TEST_PLAIN = "test-plain"
    const val EXTRA_AT = "at"
    const val EXTRA_OPEN_TASKS = "openTasks"

    /** Verpasste Erinnerungen (Handy war aus, Task kam zu spät an) werden so lange noch nachgeholt. */
    private const val CATCH_UP_MS = 24 * 60 * 60 * 1000L

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Darf die App Wecker auf die Minute genau stellen? Sonst verschiebt Android sie nach Belieben. */
    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Ist die App vom Akkusparen ausgenommen? */
    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Wann zuletzt ein Wecker dieser App ausgelöst hat (0: noch nie) und mit wie viel Verspätung. */
    fun lastFired(context: Context): Pair<Long, Long> {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        return prefs.getLong("lastFired", 0) to prefs.getLong("lastDelay", 0)
    }

    /**
     * Stellt zwei Probe-Wecker auf dieselbe Zeit in einer Minute: einen über die Weckerfunktion des
     * Geräts (so stellt die App ihre Erinnerungen) und einen gewöhnlichen. Jeder meldet sich mit
     * einer eigenen Benachrichtigung – so zeigt sich, welcher Weg auf diesem Handy ankommt.
     */
    fun test(context: Context) {
        val at = System.currentTimeMillis() + 60_000
        val alarms = context.getSystemService(AlarmManager::class.java)
        setAlarm(context, at, broadcast(context, 1, KIND_TEST_CLOCK, at = at))
        val plain = broadcast(context, 2, KIND_TEST_PLAIN, at = at)
        if (exactAllowed(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, plain)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, plain)
    }

    internal fun testFired(context: Context, kind: String, at: Long) {
        val now = System.currentTimeMillis()
        noteFired(context, now - at)
        val late = (now - at) / 1000
        val clock = kind == KIND_TEST_CLOCK
        show(
            context,
            Task(
                kind, if (clock) "Test A: Weckerfunktion" else "Test B: gewöhnlicher Wecker",
                note = if (late < 60) "Pünktlich angekommen." else "Angekommen, aber ${late / 60} Min. zu spät.",
            ),
            withDone = false,
        )
    }

    private fun noteFired(context: Context, delay: Long) {
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putLong("lastFired", System.currentTimeMillis()).putLong("lastDelay", delay.coerceAtLeast(0)).apply()
    }

    /**
     * Stellt einen Wecker über die Weckerfunktion des Geräts (wie eine Wecker-App): Sie löst als
     * einzige zuverlässig auf die Minute aus, auch wenn das Handy schläft oder Akku spart.
     * Android zeigt dafür das Wecker-Symbol in der Statusleiste.
     */
    private fun setAlarm(context: Context, at: Long, intent: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (exactAllowed(context)) {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context)), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** Öffnet die App bei den Tasks. */
    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_TASKS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Der Auftrag an den [ReminderReceiver]. Bewusst schlicht – nur die Klasse und Extras, keine
     * Aktion und keine Daten-Adresse –, damit Android ihn ohne Abgleich mit Filtern zustellt.
     * Die Aufträge unterscheiden sich über [code].
     */
    private fun broadcast(context: Context, code: Int, kind: String? = null, id: String? = null, at: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Eigene Nummer je Task: gerade für den Wecker, ungerade für „Erledigt“ (1 und 2 gehören dem Test). */
    private fun code(id: String, done: Boolean): Int = (id.hashCode() and 0x3FFFFFFF) * 2 + 4 + (if (done) 1 else 0)

    /** Betrifft die Erinnerung dieses Geräts den Task? Ja, wenn er einem selbst oder niemandem zugeordnet ist. */
    fun concerns(task: Task, myName: String): Boolean =
        task.assignees.isEmpty() || (myName.isNotBlank() && task.assignees.any { it.equals(myName, ignoreCase = true) })

    fun millis(task: Task): Long? = task.due?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    /** Gleicht die gestellten Wecker mit den Tasks ab. Darf beliebig oft aufgerufen werden. */
    @Synchronized
    fun refresh(context: Context, data: FamilyData) {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val myName = Settings.config.value.name
        val now = System.currentTimeMillis()

        val wanted = HashMap<String, Long>()
        for (task in data.tasks) {
            if (task.done || !concerns(task, myName)) continue
            wanted[task.id] = millis(task) ?: continue
        }
        val scheduled = HashSet(prefs.getStringSet("scheduled", emptySet()) ?: emptySet())
        val notified = HashSet(prefs.getStringSet("notified", emptySet()) ?: emptySet())

        // erledigt, gelöscht oder nicht mehr zugeordnet: Wecker und Benachrichtigung weg
        for (id in scheduled.toList()) {
            if (id !in wanted) {
                alarms.cancel(alarmIntent(context, id))
                scheduled -= id
            }
        }
        for (key in notified.toList()) {
            val id = key.substringBeforeLast('|')
            if (id !in wanted) {
                notifications.cancel(id.hashCode())
                notified -= key
            } else if (key != "$id|${wanted[id]}") {
                notified -= key // Zeit wurde verschoben
            }
        }

        for ((id, at) in wanted) {
            val key = "$id|$at"
            if (key in notified) continue
            if (at <= now) {
                notified += key
                scheduled -= id
                if (now - at < CATCH_UP_MS) data.tasks.firstOrNull { it.id == id }?.let { show(context, it) }
            } else {
                alarms.cancel(oldAlarmIntent(context, id))
                setAlarm(context, at, alarmIntent(context, id))
                scheduled += id
            }
        }
        prefs.edit().putStringSet("scheduled", scheduled).putStringSet("notified", notified).apply()
    }

    private fun alarmIntent(context: Context, id: String): PendingIntent = broadcast(context, code(id, false), id = id)

    /** Wecker aus Version 0.1.x (mit Aktion und Daten-Adresse) – nur noch zum Löschen. */
    private fun oldAlarmIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("io.github.haigerman.familylist.REMIND")
            .setData(Uri.parse("familylist://task/$id")).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun show(context: Context, task: Task, withDone: Boolean = true) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Erinnerungen an Tasks zur eingestellten Zeit"
                }
            )
        }
        val open = openApp(context)
        val done = broadcast(context, code(task.id, true), KIND_DONE, task.id)
        val detail = listOfNotNull(
            task.assignees.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Für "),
            task.note.takeIf { it.isNotBlank() },
        ).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_list)
            .setContentTitle(task.title.ifBlank { "Erinnerung" })
            .setContentText(detail.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setWhen(millis(task) ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFFE8C77A.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .apply { if (withDone) addAction(0, "Erledigt", done) }
            .build()
        manager.notify(task.id.hashCode(), notification)
    }

    /** Der Wecker eines Tasks hat ausgelöst. */
    internal fun fired(context: Context, id: String?) {
        Store.data.value.tasks.firstOrNull { it.id == id }?.let(::millis)?.let { noteFired(context, System.currentTimeMillis() - it) }
        // refresh zeigt fällige Erinnerungen und merkt sich, dass sie gezeigt wurden
        refresh(context, Store.data.value)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_ID)
        when (val kind = intent.getStringExtra(Reminders.EXTRA_KIND)) {
            Reminders.KIND_TEST_CLOCK, Reminders.KIND_TEST_PLAIN ->
                Reminders.testFired(context, kind, intent.getLongExtra(Reminders.EXTRA_AT, System.currentTimeMillis()))
            Reminders.KIND_DONE -> if (id != null) {
                context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
                Store.submit(TaskDone(id, true))
            }
            else -> Reminders.fired(context, id)
        }
    }
}

/** Nach Neustart, App-Update oder Zeitumstellung sind die Wecker weg bzw. falsch – neu stellen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Reminders.refresh(context, Store.data.value)
        SyncWorker.schedule(context)
    }
}
