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
import io.github.haigerman.familylist.data.Birthday
import io.github.haigerman.familylist.data.Birthdays
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Settings
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.Task
import io.github.haigerman.familylist.data.TaskDone
import java.time.LocalDate
import java.time.ZoneId

/**
 * Erinnerungen: Für jeden offenen Task mit Erinnerungszeit, der einen selbst betrifft, stellt die
 * App einen Wecker im Gerät; zur Zeit erscheint die Benachrichtigung. Tasks der anderen kommen
 * über den Abgleich (auch im Hintergrund) aufs Gerät. Dazu kommen je Geburtstag zwei feste
 * Erinnerungen für alle: am Vortag um 15:00 und am Tag um 10:00.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_ID = "id"
    /** Was der Empfänger tun soll: fehlt die Angabe, ist es der Wecker eines Tasks. */
    const val EXTRA_KIND = "kind"
    const val KIND_DONE = "done"
    const val KIND_TEST = "test"
    const val KIND_LATER = "later"
    const val KIND_TOMORROW = "tomorrow"
    const val EXTRA_AT = "at"
    const val EXTRA_OPEN_TASKS = "openTasks"
    /** Welcher Reiter beim Öffnen aus einer Benachrichtigung gezeigt wird. */
    const val EXTRA_OPEN_PAGE = "openPage"
    const val PAGE_BIRTHDAYS = 2

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

    /** Stellt einen Probe-Wecker auf genau dem Weg, den auch die Erinnerungen nehmen: in einer Minute kommt eine Test-Benachrichtigung. */
    fun test(context: Context) {
        val at = System.currentTimeMillis() + 60_000
        setAlarm(context, at, broadcast(context, 1, KIND_TEST, id = KIND_TEST, at = at))
    }

    internal fun testFired(context: Context, at: Long) {
        val now = System.currentTimeMillis()
        noteFired(context, now - at)
        val late = (now - at) / 1000
        show(
            context,
            Task(KIND_TEST, "Test-Erinnerung", note = if (late < 60) "Pünktlich angekommen – die Erinnerungen funktionieren." else "Angekommen, aber ${late / 60} Min. zu spät."),
            withDone = false,
        )
    }

    private fun noteFired(context: Context, delay: Long) {
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putLong("lastFired", System.currentTimeMillis()).putLong("lastDelay", delay.coerceAtLeast(0)).apply()
    }

    /**
     * Stellt einen Wecker auf die Minute; er löst auch aus, wenn das Handy schläft. Android kann
     * ihn dann zum Akkusparen um einige Minuten zurückhalten – das ist in Kauf genommen: Die
     * Weckerfunktion (setAlarmClock) wäre pünktlich, zeigt aber das Wecker-Symbol in der Statusleiste.
     */
    private fun setAlarm(context: Context, at: Long, intent: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (exactAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
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
     * Aktion und keine Daten-Adresse: Wecker mit Daten-Adresse wurden (Version 0.1.1) nicht zugestellt.
     * Die Aufträge unterscheiden sich über [code].
     */
    private fun broadcast(context: Context, code: Int, kind: String? = null, id: String? = null, at: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Eigene Nummer je Task: gerade für den Wecker, ungerade für „Erledigt“ (1 gehört dem Test). */
    private fun code(id: String, variant: Int): Int = (id.hashCode() and 0x0FFFFFFF) * 4 + 4 + variant

    /**
     * „Später erinnern“ gilt nur auf diesem Gerät: Der Task und seine Zeit im Space bleiben, wie
     * sie sind – wer sonst noch zugeordnet ist, wird davon nicht berührt. Gemerkt wird je Task
     * die ursprüngliche und die neue Zeit; ändert jemand die Zeit des Tasks, verfällt der Aufschub.
     */
    private fun snoozes(context: Context): Map<String, Pair<Long, Long>> =
        (context.getSharedPreferences("reminders", Context.MODE_PRIVATE).getStringSet("snoozed", emptySet()) ?: emptySet())
            .mapNotNull { entry ->
                val parts = entry.split('|')
                val from = parts.getOrNull(1)?.toLongOrNull()
                val to = parts.getOrNull(2)?.toLongOrNull()
                if (from == null || to == null) null else parts[0] to (from to to)
            }.toMap()

    private fun saveSnoozes(context: Context, snoozes: Map<String, Pair<Long, Long>>) {
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putStringSet("snoozed", snoozes.map { (id, t) -> "$id|${t.first}|${t.second}" }.toSet()).apply()
    }

    /** Erinnert auf diesem Gerät noch einmal: in einer Stunde oder morgen zur selben Uhrzeit. */
    internal fun snooze(context: Context, id: String, tomorrow: Boolean) {
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
        val task = Store.data.value.tasks.firstOrNull { it.id == id } ?: return
        val due = task.due ?: return
        val original = millis(task) ?: return
        val now = java.time.LocalDateTime.now()
        val next = if (tomorrow) now.toLocalDate().plusDays(1).atTime(due.toLocalTime()) else now.plusHours(1)
        saveSnoozes(context, snoozes(context) + (id to (original to next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())))
        refresh(context, Store.data.value)
    }

    /** Betrifft die Erinnerung dieses Geräts den Task? Ja, wenn er einem selbst oder niemandem zugeordnet ist. */
    fun concerns(task: Task, myName: String): Boolean =
        task.assignees.isEmpty() || (myName.isNotBlank() && task.assignees.any { it.equals(myName, ignoreCase = true) })

    fun millis(task: Task): Long? = task.due?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    /**
     * Gleicht die gestellten Wecker mit den Tasks ab. Darf beliebig oft aufgerufen werden: ein
     * Wecker, der schon richtig steht, wird nicht angefasst. [force] stellt alle neu – nötig nach
     * Neustart, App-Update oder Zeitumstellung, wenn das Gerät die Wecker verworfen hat.
     */
    @Synchronized
    fun refresh(context: Context, data: FamilyData, force: Boolean = false) {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val myName = Settings.config.value.name
        val now = System.currentTimeMillis()

        val snoozed = snoozes(context)
        val wanted = HashMap<String, Planned>()
        for (task in data.tasks) {
            if (task.done || !concerns(task, myName)) continue
            val at = millis(task) ?: continue
            // aufgeschoben? Gilt nur, solange die Zeit des Tasks dieselbe geblieben ist
            val time = snoozed[task.id]?.takeIf { it.first == at }?.second ?: at
            wanted[task.id] = Planned(time, time + CATCH_UP_MS) { show(context, task) }
        }
        val kept = snoozed.filter { (id, t) -> data.tasks.any { it.id == id && !it.done && millis(it) == t.first } }
        if (kept.size != snoozed.size) saveSnoozes(context, kept)

        // Geburtstage gehen alle an: fest am Vortag um 15:00 und am Tag um 10:00
        val today = LocalDate.now()
        for (b in data.birthdays) {
            val date = Birthdays.next(b, today)
            val (before, on) = Birthdays.reminders(b, today)
            val age = Birthdays.age(b, date)
            // verpasst (Handy aus)? Nachgeholt wird nur, solange die Erinnerung noch stimmt
            wanted["$BIRTHDAY_PREFIX${b.id}:before"] = Planned(ms(before), ms(date.atStartOfDay())) {
                showBirthday(context, b, "Morgen hat ${b.name} Geburtstag 🎂", age?.let { "${b.name} wird morgen $it." } ?: "Morgen gratulieren nicht vergessen.")
            }
            wanted["$BIRTHDAY_PREFIX${b.id}:on"] = Planned(ms(on), ms(date.plusDays(1).atStartOfDay())) {
                showBirthday(context, b, "Heute hat ${b.name} Geburtstag 🎂", (age?.let { "${b.name} wird heute $it – " } ?: "") + "gratulieren nicht vergessen!")
            }
        }

        val scheduled = HashSet(prefs.getStringSet("scheduled", emptySet()) ?: emptySet())
        val notified = HashSet(prefs.getStringSet("notified", emptySet()) ?: emptySet())

        // erledigt, gelöscht oder nicht mehr zugeordnet: Wecker und Benachrichtigung weg
        // (gemerkt wird je Wecker „Kennung|Zeit“)
        for (entry in scheduled.toList()) {
            val id = entry.substringBeforeLast('|')
            if (id !in wanted) {
                alarms.cancel(alarmIntent(context, id))
                scheduled -= entry
            } else if (force || entry != "$id|${wanted[id]?.at}") {
                scheduled -= entry // steht nicht (mehr) richtig: unten neu stellen
            }
        }
        for (key in notified.toList()) {
            val id = key.substringBeforeLast('|')
            if (id !in wanted) {
                notifications.cancel(if (id.startsWith(BIRTHDAY_PREFIX)) id.substringBeforeLast(':').hashCode() else id.hashCode())
                notified -= key
            } else if (key != "$id|${wanted[id]?.at}") {
                notified -= key // Zeit wurde verschoben
            }
        }

        for ((id, plan) in wanted) {
            val at = plan.at
            val key = "$id|$at"
            if (key in notified) continue
            if (at <= now) {
                notified += key
                scheduled -= key
                if (now < plan.until) plan.show()
            } else if (key !in scheduled) {
                alarms.cancel(oldAlarmIntent(context, id))
                setAlarm(context, at, alarmIntent(context, id))
                scheduled += key
            }
        }
        prefs.edit().putStringSet("scheduled", scheduled).putStringSet("notified", notified).apply()
    }

    /** Eine geplante Erinnerung: Zeit, bis wann sie verspätet noch gezeigt wird, und wie. */
    private class Planned(val at: Long, val until: Long, val show: () -> Unit)

    private const val BIRTHDAY_PREFIX = "bd:"
    private const val BIRTHDAY_CHANNEL = "birthdays"

    private fun ms(t: java.time.LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Öffnet die App beim Geburtstagskalender. */
    private fun openBirthdays(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_PAGE, PAGE_BIRTHDAYS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showBirthday(context: Context, b: Birthday, title: String, text: String) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(BIRTHDAY_CHANNEL, "Geburtstage", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Erinnerung am Vortag um 15:00 und am Geburtstag um 10:00"
            }
        )
        val notification = NotificationCompat.Builder(context, BIRTHDAY_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_list)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFFF48FB1.toInt())
            .setAutoCancel(true)
            .setContentIntent(openBirthdays(context))
            .build()
        // eine Benachrichtigung je Geburtstag: die vom Tag ersetzt die vom Vortag
        manager.notify("$BIRTHDAY_PREFIX${b.id}".hashCode(), notification)
    }

    private fun alarmIntent(context: Context, id: String): PendingIntent = broadcast(context, code(id, 0), id = id)

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
        val done = broadcast(context, code(task.id, 1), KIND_DONE, task.id)
        val later = broadcast(context, code(task.id, 2), KIND_LATER, task.id)
        val tomorrow = broadcast(context, code(task.id, 3), KIND_TOMORROW, task.id)
        val detail = listOfNotNull(
            task.assignees.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Für "),
            task.note.takeIf { it.isNotBlank() },
        ).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_list)
            .setContentTitle(task.title.ifBlank { "Erinnerung" })
            .setContentText(detail.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFFE8C77A.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .apply {
                if (withDone) {
                    addAction(0, "Erledigt", done)
                    addAction(0, "In 1 Std.", later)
                    addAction(0, "Morgen", tomorrow)
                }
            }
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
        when (intent.getStringExtra(Reminders.EXTRA_KIND)) {
            Reminders.KIND_TEST -> Reminders.testFired(context, intent.getLongExtra(Reminders.EXTRA_AT, System.currentTimeMillis()))
            Reminders.KIND_LATER, Reminders.KIND_TOMORROW ->
                if (id != null) Reminders.snooze(context, id, intent.getStringExtra(Reminders.EXTRA_KIND) == Reminders.KIND_TOMORROW)
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
        Reminders.refresh(context, Store.data.value, force = true)
        SyncWorker.schedule(context)
    }
}
