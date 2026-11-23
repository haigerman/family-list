package io.github.haigerman.familylist

import io.github.haigerman.familylist.data.Birthday
import io.github.haigerman.familylist.data.BirthdayDelete
import io.github.haigerman.familylist.data.BirthdayPut
import io.github.haigerman.familylist.data.Birthdays
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Markdown
import io.github.haigerman.familylist.data.OpCodec
import io.github.haigerman.familylist.data.apply
import io.github.haigerman.familylist.ui.parseDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BirthdaysTest {
    private val oma = Birthday("o1", "Oma Erna", 3, 14, 1948, "David")
    private val mia = Birthday("m1", "Mia", 11, 23, null, "")
    private val leap = Birthday("l1", "Lea", 2, 29, 2004)

    @Test fun fileRoundTripSortedByCalendar() {
        val text = Markdown.writeBirthdays(listOf(mia, oma, leap))
        assertEquals(
            "# Geburtstage\n\n" +
                "- Lea 🎂 29.02.2004 <!-- id:l1 -->\n" +
                "- Oma Erna 🎂 14.03.1948 <!-- id:o1 von:David -->\n" +
                "- Mia 🎂 23.11. <!-- id:m1 -->\n",
            text,
        )
        assertEquals(listOf(leap, oma, mia), Markdown.parseBirthdays(text))
    }

    @Test fun handWrittenLines() {
        val list = Markdown.parseBirthdays("# Geburtstage\n- Opa 🎂 1.5.1950\n- Kaputt 🎂 31.02.\n- ohne Datum\n- 🎂 01.01.")
        assertEquals(listOf("Opa"), list.map { it.name })
        assertEquals(5, list[0].month)
        assertEquals(1, list[0].day)
    }

    @Test fun nextBirthdayAndAge() {
        val today = LocalDate.of(2026, 11, 23)
        assertEquals(LocalDate.of(2026, 11, 23), Birthdays.next(mia, today))
        assertEquals(0L, Birthdays.daysUntil(mia, today))
        assertEquals(LocalDate.of(2027, 3, 14), Birthdays.next(oma, today))
        assertEquals(79, Birthdays.age(oma, Birthdays.next(oma, today)))
        assertNull(Birthdays.age(mia, today))
        // 29. Februar: in Nicht-Schaltjahren am 28.
        assertEquals(LocalDate.of(2027, 2, 28), Birthdays.next(leap, today))
        assertEquals(LocalDate.of(2028, 2, 29), Birthdays.next(leap, LocalDate.of(2027, 3, 1)))
        assertEquals(listOf(mia, leap, oma), Birthdays.upcoming(listOf(oma, leap, mia), today))
    }

    @Test fun fixedReminderTimes() {
        val (before, on) = Birthdays.reminders(oma, LocalDate.of(2026, 11, 23))
        assertEquals(LocalDateTime.of(2027, 3, 13, 15, 0), before)
        assertEquals(LocalDateTime.of(2027, 3, 14, 10, 0), on)
        // am 1. Januar liegt die Vortags-Erinnerung im alten Jahr
        val (b2, _) = Birthdays.reminders(Birthday("n", "Neu", 1, 1), LocalDate.of(2026, 12, 2))
        assertEquals(LocalDateTime.of(2026, 12, 31, 15, 0), b2)
    }

    @Test fun dateInput() {
        assertEquals(Triple(14, 3, null), parseDate("1403"))
        assertEquals(Triple(14, 3, 1948), parseDate("14031948"))
        assertEquals(Triple(29, 2, null), parseDate("2902"))
        assertNull(parseDate("29022001"))
        assertNull(parseDate("3104"))
        assertNull(parseDate("140"))
        assertNull(parseDate("14031"))
        assertNull(parseDate("14031850"))
    }

    @Test fun opsAndQueue() {
        val ops = listOf(BirthdayPut(oma), BirthdayPut(mia), BirthdayPut(oma.copy(name = "Oma", by = "Anna")), BirthdayDelete(listOf("m1")))
        assertEquals(ops, OpCodec.decode(OpCodec.encode(ops)))
        // wer ihn eingetragen hat, bleibt beim Ändern erhalten
        assertEquals(listOf(oma.copy(name = "Oma")), FamilyData().apply(ops).birthdays)
    }
}
