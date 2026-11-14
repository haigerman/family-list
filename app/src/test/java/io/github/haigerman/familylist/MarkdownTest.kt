package io.github.haigerman.familylist

import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Markdown
import io.github.haigerman.familylist.data.Member
import io.github.haigerman.familylist.data.MemberPut
import io.github.haigerman.familylist.data.OpCodec
import io.github.haigerman.familylist.data.ShopDelete
import io.github.haigerman.familylist.data.ShopDone
import io.github.haigerman.familylist.data.ShopItem
import io.github.haigerman.familylist.data.ShopPut
import io.github.haigerman.familylist.data.Task
import io.github.haigerman.familylist.data.TaskDelete
import io.github.haigerman.familylist.data.TaskDone
import io.github.haigerman.familylist.data.TaskPut
import io.github.haigerman.familylist.data.TaskRetag
import io.github.haigerman.familylist.data.apply
import io.github.haigerman.familylist.data.cleanName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MarkdownTest {
    @Test fun shopRoundTrip() {
        val items = listOf(
            ShopItem("a1", "Milch (2 l)", false, "Anna Maria"),
            ShopItem("b2", "Brot", true, "David"),
            ShopItem("c3", "Äpfel & Birnen", false, ""),
        )
        val text = Markdown.writeShop(items)
        assertEquals(
            "# Einkaufsliste\n\n- [ ] Milch (2 l) <!-- id:a1 von:Anna_Maria -->\n- [x] Brot <!-- id:b2 von:David -->\n- [ ] Äpfel & Birnen <!-- id:c3 -->\n",
            text,
        )
        assertEquals(items, Markdown.parseShop(text))
    }

    @Test fun handWrittenShopLines() {
        val items = Markdown.parseShop("# Einkaufsliste\n\nIrgendein Text\n- Butter\n* [X] Käse\n- [ ] Butter\n\n- [ ]   \n")
        assertEquals(listOf("Butter", "Käse", "Butter"), items.map { it.text })
        assertEquals(listOf(false, true, false), items.map { it.done })
        // Kennungen sind eindeutig und bei jedem Lesen dieselben
        assertEquals(3, items.map { it.id }.toSet().size)
        assertEquals(items, Markdown.parseShop("- Butter\n* [X] Käse\n- [ ] Butter"))
    }

    @Test fun tasksRoundTrip() {
        val tasks = listOf(
            Task("t1", "Müll rausbringen", false, listOf("David", "Anna Maria"), LocalDateTime.of(2026, 11, 15, 18, 0), "Gelbe Tonne\nnicht vergessen", "David"),
            Task("t2", "Oma anrufen", true, emptyList(), null, "", "Anna Maria"),
            Task("t3", "Zahnarzt", false, listOf("David"), LocalDateTime.of(2027, 1, 3, 7, 5), "", ""),
        )
        val text = Markdown.writeTasks(tasks)
        assertEquals(
            "# Tasks\n\n" +
                "- [ ] Müll rausbringen @David @Anna_Maria ⏰ 2026-11-15 18:00 <!-- id:t1 von:David -->\n" +
                "  Gelbe Tonne\n  nicht vergessen\n" +
                "- [x] Oma anrufen <!-- id:t2 von:Anna_Maria -->\n" +
                "- [ ] Zahnarzt @David ⏰ 2027-01-03 07:05 <!-- id:t3 -->\n",
            text,
        )
        assertEquals(tasks, Markdown.parseTasks(text))
    }

    @Test fun handWrittenTasks() {
        val tasks = Markdown.parseTasks("- [ ] @Anna Auto waschen ⏰ 2026-12-01\n    mit Wachs\n\n- Mail an info@example.com schreiben ⏰ 2026-12-02T8:30")
        assertEquals("Auto waschen", tasks[0].title)
        assertEquals(listOf("Anna"), tasks[0].assignees)
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), tasks[0].due)
        assertEquals("mit Wachs", tasks[0].note)
        assertEquals("Mail an info@example.com schreiben", tasks[1].title)
        assertTrue(tasks[1].assignees.isEmpty())
        assertEquals(LocalDateTime.of(2026, 12, 2, 8, 30), tasks[1].due)
    }

    @Test fun familyRoundTrip() {
        val members = listOf(Member("m1", "David", 0xE8C77A), Member("m2", "Anna Maria", 0x0000FF))
        val text = Markdown.writeFamily(members)
        assertEquals("# Familie\n\n- David (#E8C77A) <!-- id:m1 -->\n- Anna Maria (#0000FF) <!-- id:m2 -->\n", text)
        assertEquals(members, Markdown.parseFamily(text))
    }

    @Test fun textCannotBreakTheFormat() {
        val item = ShopItem("a", "Trick --> <!-- id:zzz -->\nzweite Zeile", false, "Bob")
        val parsed = Markdown.parseShop(Markdown.writeShop(listOf(item))).single()
        assertEquals("a", parsed.id)
        assertEquals("Bob", parsed.by)
        assertEquals("", cleanName(" <@_> "))
        assertEquals("Anna Maria", cleanName("  Anna_Maria "))
    }

    @Test fun opsMergeIntoRemoteState() {
        val remote = FamilyData(
            shop = listOf(ShopItem("a", "Milch"), ShopItem("b", "Brot")),
            tasks = listOf(Task("t", "Müll", assignees = listOf("David", "Anna")), Task("u", "Alt", done = true)),
            members = listOf(Member("m1", "David", 1), Member("old", "Anna", 2)),
        )
        val ops = listOf(
            ShopPut(ShopItem("c", "Eier", false, "Anna")),
            ShopPut(ShopItem("a", "Hafermilch")),
            ShopDone("b", true),
            ShopDone("weg", true), // inzwischen von jemand anderem gelöscht
            ShopDelete(listOf("b")),
            TaskDone("t", true),
            TaskPut(Task("t", "Müll rausbringen", done = false, assignees = listOf("David"))),
            TaskRetag("david", "Dave"),
            TaskDelete(listOf("u")),
            MemberPut(Member("m1", "Dave", 5)),
            MemberPut(Member("new", "anna", 7)),
        )
        // Speichern und Laden der Warteschlange ändert nichts
        assertEquals(ops, OpCodec.decode(OpCodec.encode(ops)))
        val result = remote.apply(OpCodec.decode(OpCodec.encode(ops)))
        assertEquals(listOf(ShopItem("a", "Hafermilch"), ShopItem("c", "Eier", false, "Anna")), result.shop)
        // „erledigt“ bleibt beim Ändern erhalten
        assertEquals(listOf(Task("t", "Müll rausbringen", done = true, assignees = listOf("Dave"))), result.tasks)
        assertEquals(listOf(Member("m1", "Dave", 5), Member("new", "anna", 7)), result.members)
    }
}
