package io.github.haigerman.familylist

import io.github.haigerman.familylist.ui.dueLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DueLabelTest {
    private val due = LocalDateTime.of(2026, 11, 24, 7, 0)

    /** Derselbe Termin heißt je nach Tag „Morgen“, „Heute“ oder „Gestern“. */
    @Test fun followsTheDay() {
        assertEquals("Morgen, 07:00", dueLabel(due, LocalDate.of(2026, 11, 23)))
        assertEquals("Heute, 07:00", dueLabel(due, LocalDate.of(2026, 11, 24)))
        assertEquals("Gestern, 07:00", dueLabel(due, LocalDate.of(2026, 11, 25)))
        assertEquals("Do., 26. Nov., 07:00", dueLabel(due.plusDays(2), LocalDate.of(2026, 11, 24)))
    }
}
