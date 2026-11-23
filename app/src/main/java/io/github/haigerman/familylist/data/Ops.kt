package io.github.haigerman.familylist.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Eine Änderung, die noch in den Space geschrieben werden muss. Änderungen werden auf den
 * jeweils neuesten Stand der Datei angewendet – so gehen Einträge der anderen nie verloren.
 */
sealed interface Op {
    val doc: Doc
}

/** Neuer Eintrag; gibt es die Kennung schon, wird nur der Text ersetzt. */
data class ShopPut(val item: ShopItem) : Op { override val doc get() = Doc.SHOP }
data class ShopDone(val id: String, val done: Boolean) : Op { override val doc get() = Doc.SHOP }
data class ShopDelete(val ids: List<String>) : Op { override val doc get() = Doc.SHOP }

/** Neuer Task; gibt es die Kennung schon, wird er ersetzt (nur „erledigt“ bleibt, wie es ist). */
data class TaskPut(val task: Task) : Op { override val doc get() = Doc.TASKS }
data class TaskDone(val id: String, val done: Boolean) : Op { override val doc get() = Doc.TASKS }
data class TaskDelete(val ids: List<String>) : Op { override val doc get() = Doc.TASKS }
/** Ein Familienmitglied hat seinen Namen geändert: Zuordnungen ziehen mit. */
data class TaskRetag(val old: String, val new: String) : Op { override val doc get() = Doc.TASKS }

data class MemberPut(val member: Member) : Op { override val doc get() = Doc.FAMILY }

/** Neuer Geburtstag; gibt es die Kennung schon, wird er ersetzt. */
data class BirthdayPut(val birthday: Birthday) : Op { override val doc get() = Doc.BIRTHDAYS }
data class BirthdayDelete(val ids: List<String>) : Op { override val doc get() = Doc.BIRTHDAYS }

fun FamilyData.apply(ops: List<Op>): FamilyData = ops.fold(this) { data, op -> data.apply(op) }

fun FamilyData.apply(op: Op): FamilyData = when (op) {
    is ShopPut ->
        if (shop.any { it.id == op.item.id }) copy(shop = shop.map { if (it.id == op.item.id) it.copy(text = op.item.text) else it })
        else copy(shop = shop + op.item)
    is ShopDone -> copy(shop = shop.map { if (it.id == op.id) it.copy(done = op.done) else it })
    is ShopDelete -> copy(shop = shop.filter { it.id !in op.ids })
    is TaskPut ->
        if (tasks.any { it.id == op.task.id }) copy(tasks = tasks.map { if (it.id == op.task.id) op.task.copy(done = it.done, by = it.by) else it })
        else copy(tasks = tasks + op.task)
    is TaskDone -> copy(tasks = tasks.map { if (it.id == op.id) it.copy(done = op.done) else it })
    is TaskDelete -> copy(tasks = tasks.filter { it.id !in op.ids })
    is TaskRetag -> copy(tasks = tasks.map { t ->
        if (t.assignees.none { it.equals(op.old, ignoreCase = true) }) t
        else t.copy(assignees = t.assignees.map { if (it.equals(op.old, ignoreCase = true)) op.new else it }.distinctBy { it.lowercase() })
    })
    is BirthdayPut ->
        if (birthdays.any { it.id == op.birthday.id }) copy(birthdays = birthdays.map { if (it.id == op.birthday.id) op.birthday.copy(by = it.by) else it })
        else copy(birthdays = birthdays + op.birthday)
    is BirthdayDelete -> copy(birthdays = birthdays.filter { it.id !in op.ids })
    is MemberPut -> {
        val m = op.member
        // derselbe Name auf einem neu eingerichteten Gerät ersetzt den alten Eintrag
        val others = members.filter { it.id == m.id || !it.name.equals(m.name, ignoreCase = true) }
        copy(members = if (others.any { it.id == m.id }) others.map { if (it.id == m.id) m else it } else others + m)
    }
}

/** Speichert wartende Änderungen, damit sie einen App-Neustart ohne Netz überstehen. */
object OpCodec {
    fun encode(ops: List<Op>): String = JSONArray().apply { ops.forEach { put(toJson(it)) } }.toString()

    fun decode(text: String?): List<Op> {
        if (text.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJson(op: Op): JSONObject = JSONObject().apply {
        when (op) {
            is ShopPut -> { put("t", "shopPut"); put("id", op.item.id); put("text", op.item.text); put("done", op.item.done); put("by", op.item.by) }
            is ShopDone -> { put("t", "shopDone"); put("id", op.id); put("done", op.done) }
            is ShopDelete -> { put("t", "shopDelete"); put("ids", JSONArray(op.ids)) }
            is TaskPut -> {
                put("t", "taskPut"); put("id", op.task.id); put("text", op.task.title); put("done", op.task.done)
                put("who", JSONArray(op.task.assignees)); put("due", op.task.due?.toString() ?: ""); put("note", op.task.note); put("by", op.task.by)
            }
            is TaskDone -> { put("t", "taskDone"); put("id", op.id); put("done", op.done) }
            is TaskDelete -> { put("t", "taskDelete"); put("ids", JSONArray(op.ids)) }
            is TaskRetag -> { put("t", "taskRetag"); put("old", op.old); put("new", op.new) }
            is BirthdayPut -> {
                put("t", "birthdayPut"); put("id", op.birthday.id); put("text", op.birthday.name); put("month", op.birthday.month)
                put("day", op.birthday.day); put("year", op.birthday.year ?: 0); put("by", op.birthday.by)
            }
            is BirthdayDelete -> { put("t", "birthdayDelete"); put("ids", JSONArray(op.ids)) }
            is MemberPut -> { put("t", "memberPut"); put("id", op.member.id); put("text", op.member.name); put("color", op.member.color) }
        }
    }

    private fun strings(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun fromJson(o: JSONObject): Op? = when (o.optString("t")) {
        "shopPut" -> ShopPut(ShopItem(o.getString("id"), o.optString("text"), o.optBoolean("done"), o.optString("by")))
        "shopDone" -> ShopDone(o.getString("id"), o.optBoolean("done"))
        "shopDelete" -> ShopDelete(strings(o, "ids"))
        "taskPut" -> TaskPut(
            Task(
                o.getString("id"), o.optString("text"), o.optBoolean("done"), strings(o, "who"),
                o.optString("due").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                o.optString("note"), o.optString("by"),
            )
        )
        "taskDone" -> TaskDone(o.getString("id"), o.optBoolean("done"))
        "taskDelete" -> TaskDelete(strings(o, "ids"))
        "taskRetag" -> TaskRetag(o.optString("old"), o.optString("new"))
        "birthdayPut" -> BirthdayPut(
            Birthday(
                o.getString("id"), o.optString("text"), o.optInt("month"), o.optInt("day"),
                o.optInt("year").takeIf { it > 0 }, o.optString("by"),
            )
        )
        "birthdayDelete" -> BirthdayDelete(strings(o, "ids"))
        "memberPut" -> MemberPut(Member(o.getString("id"), o.optString("text"), o.optInt("color")))
        else -> null
    }
}
