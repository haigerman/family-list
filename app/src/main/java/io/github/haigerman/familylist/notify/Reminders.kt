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
    private const val ACTION_REMIND = "io.github.haigerman.familylist.REMIND"
    const val ACTION_DONE = "io.github.haigerman.familylist.DONE"
    const val EXTRA_ID = "id"
    const val EXTRA_OPEN_TASKS = "openTasks"

    /** Verpasste Erinnerungen (Handy war aus, Task kam zu spät an) werden so lange noch nachgeholt. */
    private const val CATCH_UP_MS = 24 * 60 * 60 * 1000L

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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
                val intent = alarmIntent(context, id)
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                }
                scheduled += id
            }
        }
        prefs.edit().putStringSet("scheduled", scheduled).putStringSet("notified", notified).apply()
    }

    private fun alarmIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).setData(Uri.parse("familylist://task/$id")).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun show(context: Context, task: Task) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Erinnerungen an Tasks zur eingestellten Zeit"
                }
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_TASKS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).setData(Uri.parse("familylist://task/${task.id}")).putExtra(EXTRA_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
            .addAction(0, "Erledigt", done)
            .build()
        manager.notify(task.id.hashCode(), notification)
    }

    /** Der Wecker eines Tasks hat ausgelöst. */
    internal fun fired(context: Context, id: String) {
        // refresh zeigt fällige Erinnerungen und merkt sich, dass sie gezeigt wurden
        refresh(context, Store.data.value)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        when (intent.action) {
            Reminders.ACTION_DONE -> {
                context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
                Store.submit(TaskDone(id, true))
                SyncWorker.kick(context)
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
