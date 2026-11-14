package io.github.haigerman.familylist.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Liest und schreibt die Markdown-Dateien im Space. Sie bleiben von Hand lesbar und änderbar:
 *
 * ```
 * - [ ] Milch <!-- id:k3f9x2ab von:Anna -->
 * - [x] Müll rausbringen @David @Anna ⏰ 2026-11-15 18:00 <!-- id:p0q1r2s3 von:David -->
 *   Gelbe Tonne nicht vergessen
 * ```
 *
 * Der Kommentar am Zeilenende trägt die Kennung, über die Änderungen mehrerer Geräte
 * zusammenfinden. Zeilen ohne Kommentar (von Hand ergänzt) bekommen eine Kennung aus ihrem Text.
 */
object Markdown {
    private val ITEM = Regex("^ ?[-*+]\\s+(?:\\[([ xX])]\\s*)?(.*)$")
    private val META = Regex("\\s*<!--(.*?)-->\\s*$")
    private val DUE = Regex("⏰\\s*(\\d{4}-\\d{2}-\\d{2})(?:[ T](\\d{1,2}:\\d{2}))?")
    private val TAG = Regex("(?:^|(?<=\\s))@([^\\s@]+)")
    private val COLOR = Regex("\\s*\\(#([0-9a-fA-F]{6})\\)\\s*$")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val TIME_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ---------------------------------------------------------------- Einkaufsliste

    fun parseShop(text: String): List<ShopItem> {
        val ids = IdSource()
        return text.lines().mapNotNull { line ->
            val line0 = parseLine(line) ?: return@mapNotNull null
            if (line0.body.isEmpty()) return@mapNotNull null
            ShopItem(ids.of(line0), line0.body, line0.done, line0.meta["von"].orEmpty())
        }
    }

    fun writeShop(items: List<ShopItem>): String = buildString {
        append("# Einkaufsliste\n\n")
        for (item in items) {
            append(if (item.done) "- [x] " else "- [ ] ").append(oneLine(item.text))
            append(meta("id" to item.id, "von" to item.by)).append('\n')
        }
    }

    // ---------------------------------------------------------------- Tasks

    fun parseTasks(text: String): List<Task> {
        val ids = IdSource()
        val tasks = ArrayList<Task>()
        for (line in text.lines()) {
            val parsed = parseLine(line)
            if (parsed == null) {
                // eingerückte Zeilen unter einem Task sind seine Notiz
                if (tasks.isNotEmpty() && line.isNotBlank() && (line.startsWith("  ") || line.startsWith("\t"))) {
                    val last = tasks.last()
                    tasks[tasks.size - 1] = last.copy(note = if (last.note.isEmpty()) line.trim() else last.note + "\n" + line.trim())
                }
                continue
            }
            var body = parsed.body
            var due: LocalDateTime? = null
            DUE.find(body)?.let { m ->
                due = try {
                    val day = LocalDate.parse(m.groupValues[1], DAY)
                    val time = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, TIME) } ?: LocalTime.of(9, 0)
                    LocalDateTime.of(day, time)
                } catch (_: Exception) {
                    null
                }
                body = body.removeRange(m.range)
            }
            val assignees = TAG.findAll(body).map { decode(it.groupValues[1]) }.distinct().toList()
            val title = TAG.replace(body, "").replace(Regex("\\s+"), " ").trim()
            if (title.isEmpty() && assignees.isEmpty()) continue
            tasks += Task(ids.of(parsed), title, parsed.done, assignees, due, "", parsed.meta["von"].orEmpty())
        }
        return tasks
    }

    fun writeTasks(tasks: List<Task>): String = buildString {
        append("# Tasks\n\n")
        for (task in tasks) {
            append(if (task.done) "- [x] " else "- [ ] ").append(oneLine(task.title))
            for (name in task.assignees) append(" @").append(encode(name))
            task.due?.let { append(" ⏰ ").append(it.format(DAY)).append(' ').append(it.format(TIME_OUT)) }
            append(meta("id" to task.id, "von" to task.by)).append('\n')
            for (noteLine in task.note.lines()) {
                if (noteLine.isNotBlank()) append("  ").append(oneLine(noteLine)).append('\n')
            }
        }
    }

    // ---------------------------------------------------------------- Familie

    fun parseFamily(text: String): List<Member> {
        val ids = IdSource()
        return text.lines().mapNotNull { line ->
            val parsed = parseLine(line) ?: return@mapNotNull null
            val color = COLOR.find(parsed.body)
            val name = cleanName(if (color != null) parsed.body.removeRange(color.range) else parsed.body)
            if (name.isEmpty()) return@mapNotNull null
            Member(ids.of(parsed), name, color?.groupValues?.get(1)?.toInt(16) ?: fallbackColor(name))
        }
    }

    fun writeFamily(members: List<Member>): String = buildString {
        append("# Familie\n\n")
        for (m in members) {
            append("- ").append(m.name).append(" (#").append("%06X".format(m.color and 0xFFFFFF)).append(')')
            append(meta("id" to m.id)).append('\n')
        }
    }

    // ----------------------------------------------------------------

    private class Line(val done: Boolean, val body: String, val meta: Map<String, String>)

    private fun parseLine(line: String): Line? {
        val m = ITEM.matchEntire(line.trimEnd()) ?: return null
        var body = m.groupValues[2]
        val meta = HashMap<String, String>()
        META.find(body)?.let { c ->
            for (pair in c.groupValues[1].trim().split(Regex("\\s+"))) {
                val key = pair.substringBefore(':', "")
                if (key.isNotEmpty()) meta[key] = decode(pair.substringAfter(':'))
            }
            body = body.removeRange(c.range)
        }
        return Line(m.groupValues[1].equals("x", ignoreCase = true), body.trim(), meta)
    }

    /** Vergibt Kennungen: die aus dem Kommentar, sonst eine aus dem Text (bei Dopplungen durchgezählt). */
    private class IdSource {
        private val seen = HashSet<String>()
        fun of(line: Line): String {
            val base = line.meta["id"]?.takeIf { it.isNotEmpty() } ?: ("x" + Integer.toHexString(line.body.hashCode()))
            var id = base
            var n = 2
            while (!seen.add(id)) id = base + "-" + n++
            return id
        }
    }

    private fun meta(vararg pairs: Pair<String, String>): String {
        val parts = pairs.filter { it.second.isNotBlank() }.joinToString(" ") { it.first + ":" + encode(it.second) }
        return if (parts.isEmpty()) "" else " <!-- $parts -->"
    }

    private fun encode(value: String) = value.trim().replace(Regex("\\s+"), "_").replace("--", "-").replace(">", "")
    private fun decode(value: String) = value.replace('_', ' ').trim()

    private fun oneLine(text: String) =
        text.replace("<!--", "").replace("-->", "").replace(Regex("\\s+"), " ").trim()
}
