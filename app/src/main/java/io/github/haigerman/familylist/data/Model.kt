package io.github.haigerman.familylist.data

import java.time.LocalDateTime

/** Die Dateien im OpenCloud-Space. */
enum class Doc(val file: String) {
    SHOP("Einkaufsliste.md"),
    TASKS("Tasks.md"),
    FAMILY("Familie.md"),
    BIRTHDAYS("Geburtstage.md"),
}

data class ShopItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
    /** Name dessen, der den Eintrag geschrieben hat. */
    val by: String = "",
)

data class Task(
    val id: String,
    val title: String,
    val done: Boolean = false,
    /** Namen der Familienmitglieder, denen der Task zugeordnet ist. Leer: geht alle an. */
    val assignees: List<String> = emptyList(),
    /** Erinnerungszeit (Ortszeit) oder null. */
    val due: LocalDateTime? = null,
    val note: String = "",
    val by: String = "",
)

data class Birthday(
    val id: String,
    val name: String,
    val month: Int,
    val day: Int,
    /** Geburtsjahr, wenn bekannt – dann zeigt die App, wie alt jemand wird. */
    val year: Int? = null,
    val by: String = "",
)

data class Member(
    val id: String,
    val name: String,
    /** Farbe des Tags als 0xRRGGBB. */
    val color: Int,
)

data class FamilyData(
    val shop: List<ShopItem> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val members: List<Member> = emptyList(),
    val birthdays: List<Birthday> = emptyList(),
) {
    fun colorOf(name: String): Int =
        members.firstOrNull { it.name.equals(name, ignoreCase = true) }?.color ?: fallbackColor(name)
}

/** Die Farben, die für den eigenen Tag zur Wahl stehen. */
val TAG_COLORS = listOf(
    0xE8C77A, 0xFF8A65, 0x7FD1AE, 0x82B1FF, 0xB39DDB, 0xF48FB1,
    0x4DD0E1, 0xC5E1A5, 0xFFB74D, 0xEF5350, 0xF2C94C, 0xB0BEC5,
)

/** Farbe für Namen, die (noch) nicht in der Familienliste stehen: immer dieselbe je Name. */
fun fallbackColor(name: String): Int =
    TAG_COLORS[Math.floorMod(name.trim().lowercase().hashCode(), TAG_COLORS.size)]

/** Macht aus einer Eingabe einen Namen, der sich als Tag schreiben lässt. */
fun cleanName(input: String): String =
    input.replace(Regex("[<>@_()\\[\\]\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(30)

fun newId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val random = java.security.SecureRandom()
    return String(CharArray(8) { chars[random.nextInt(chars.length)] })
}
