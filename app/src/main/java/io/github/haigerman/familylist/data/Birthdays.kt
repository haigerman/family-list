package io.github.haigerman.familylist.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Rechnet mit Geburtstagen. Die Erinnerungen stehen fest und lassen sich nicht ändern:
 * am Vortag um 15:00 und am Geburtstag um 10:00 – für alle in der Familie.
 */
object Birthdays {
    val REMIND_BEFORE: LocalTime = LocalTime.of(15, 0)
    val REMIND_ON: LocalTime = LocalTime.of(10, 0)

    /** So viele kommende Geburtstage zeigt der Reiter. */
    const val SHOWN = 5

    /** Der Geburtstag im Jahr [year]; am 29. Februar in Nicht-Schaltjahren der 28. */
    fun inYear(b: Birthday, year: Int): LocalDate {
        val day = if (b.month == 2 && b.day == 29 && !java.time.Year.isLeap(year.toLong())) 28 else b.day
        return LocalDate.of(year, b.month, day)
    }

    /** Der nächste Geburtstag ab [today] – ist heute der Geburtstag, dann heute. */
    fun next(b: Birthday, today: LocalDate): LocalDate {
        val thisYear = inYear(b, today.year)
        return if (thisYear.isBefore(today)) inYear(b, today.year + 1) else thisYear
    }

    fun daysUntil(b: Birthday, today: LocalDate): Long = ChronoUnit.DAYS.between(today, next(b, today))

    /** Wie alt jemand an seinem Geburtstag [on] wird, wenn das Jahr bekannt ist. */
    fun age(b: Birthday, on: LocalDate): Int? = b.year?.let { on.year - it }?.takeIf { it >= 0 }

    /** Alle Geburtstage, der nächste zuerst. */
    fun upcoming(list: List<Birthday>, today: LocalDate): List<Birthday> =
        list.sortedWith(compareBy<Birthday> { daysUntil(it, today) }.thenBy { it.name.lowercase() })

    /** Ist das ein gültiger Tag (der 29. Februar zählt)? */
    fun valid(day: Int, month: Int, year: Int?): Boolean {
        if (month !in 1..12 || day < 1) return false
        val max = if (year != null) java.time.YearMonth.of(year, month).lengthOfMonth() else java.time.Month.of(month).maxLength()
        return day <= max
    }

    /** Die beiden Erinnerungen zum nächsten Geburtstag: am Vortag und am Tag selbst. */
    fun reminders(b: Birthday, today: LocalDate): Pair<LocalDateTime, LocalDateTime> {
        val date = next(b, today)
        return date.minusDays(1).atTime(REMIND_BEFORE) to date.atTime(REMIND_ON)
    }

    /** „in 12 Tagen“, „morgen“, „heute“ */
    fun whenLabel(days: Long): String = when (days) {
        0L -> "heute"
        1L -> "morgen"
        else -> "in $days Tagen"
    }
}
