package io.github.haigerman.familylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haigerman.familylist.data.Birthday
import io.github.haigerman.familylist.data.BirthdayDelete
import io.github.haigerman.familylist.data.BirthdayPut
import io.github.haigerman.familylist.data.Birthdays
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.newId
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rosa für Geburtstage – passt zum Sand der App und hebt den heutigen Tag hervor. */
private val Rose = androidx.compose.ui.graphics.Color(0xFFF48FB1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysTab(data: FamilyData, myName: String) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Birthday?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val today = rememberNow().toLocalDate()

    val sorted = Birthdays.upcoming(data.birthdays, today)
    val shown = if (showAll) sorted else sorted.take(Birthdays.SHOWN)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showAll) "Alle Geburtstage" else "Die nächsten Geburtstage",
                fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            PrimaryButton("＋ Geburtstag") { creating = true }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch { Store.sync(); refreshing = false }
            },
            modifier = Modifier.weight(1f),
        ) {
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Noch keine Geburtstage.\nMit „Geburtstag“ trägst du einen ein – alle in der Familie werden am Vortag um 15:00 und am Tag um 10:00 erinnert.",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { b ->
                    BirthdayRow(b, today, Modifier.animateItem()) { editing = b }
                }
                if (sorted.size > Birthdays.SHOWN) {
                    item(key = "more") {
                        TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAll) "Nur die nächsten ${Birthdays.SHOWN}" else "Alle anzeigen (${sorted.size})", color = Sand, fontSize = 14.sp)
                        }
                    }
                }
                if (sorted.isNotEmpty()) {
                    item(key = "hint") {
                        Text(
                            "🔔 Alle in der Familie werden am Vortag um 15:00 und am Geburtstag um 10:00 erinnert.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        BirthdayEditor(editing, myName, onDismiss = { creating = false; editing = null })
    }
}

@Composable
private fun BirthdayRow(b: Birthday, today: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val date = Birthdays.next(b, today)
    val days = Birthdays.daysUntil(b, today)
    val age = Birthdays.age(b, date)
    val isToday = days == 0L
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isToday) Rose.copy(alpha = 0.16f) else Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kalenderblatt: Tag und Monat
        Column(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isToday) Rose.copy(alpha = 0.3f) else CardOn),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${date.dayOfMonth}", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = if (isToday) Rose else Sand)
            Text(date.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)).trimEnd('.'), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(b.name, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val weekday = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
            Text(
                listOfNotNull(age?.let { if (isToday) "wird heute $it" else "wird $it" }, if (isToday) null else weekday).joinToString(" · "),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (isToday) "heute 🎂" else Birthdays.whenLabel(days), fontSize = 14.sp,
            fontWeight = if (days <= 1) FontWeight.Medium else FontWeight.Normal,
            color = when {
                isToday -> Rose
                days <= 7 -> Sand
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Legt einen Geburtstag an ([birthday] ist null) oder ändert ihn. */
@Composable
private fun BirthdayEditor(birthday: Birthday?, myName: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(birthday?.name.orEmpty()) }
    // nur die Ziffern; die Punkte setzt die Anzeige (TTMMJJJJ → TT.MM.JJJJ)
    var digits by remember {
        mutableStateOf(birthday?.let { "%02d%02d".format(it.day, it.month) + (it.year?.toString() ?: "") } ?: "")
    }
    val parsed = parseDate(digits)
    val error = when {
        digits.isEmpty() -> null
        parsed == null && digits.length in setOf(4, 8) -> "Diesen Tag gibt es nicht."
        parsed == null && digits.length > 4 -> "Das Jahr bitte vierstellig."
        else -> null
    }

    fun save() {
        val (day, month, year) = parsed ?: return
        val result = Birthday(birthday?.id ?: newId(), name.trim().replace(Regex("\\s+"), " "), month, day, year, birthday?.by ?: myName)
        if (result != birthday) Store.submit(BirthdayPut(result))
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text(if (birthday == null) "Neuer Geburtstag" else "Geburtstag") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Wer?") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = digits,
                    onValueChange = { digits = it.filter(Char::isDigit).take(8) },
                    label = { Text("Geburtstag") },
                    placeholder = { Text("TT.MM.JJJJ") },
                    supportingText = { Text(error ?: "Das Jahr kann fehlen, z. B. 14.03.", color = if (error != null) Coral else MaterialTheme.colorScheme.onSurfaceVariant) },
                    isError = error != null,
                    singleLine = true,
                    visualTransformation = DateDots,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "🔔 Erinnerung für alle: am Vortag um 15:00 und am Geburtstag um 10:00.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && parsed != null, onClick = { save() }) { Text("Speichern", color = Sand) }
        },
        dismissButton = {
            Row {
                if (birthday != null) {
                    TextButton(onClick = { Store.submit(BirthdayDelete(listOf(birthday.id))); onDismiss() }) { Text("Löschen", color = Coral) }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

/** „1403“ → 14. März ohne Jahr, „14031948“ → mit Jahr; null, wenn unvollständig oder ungültig. */
internal fun parseDate(digits: String): Triple<Int, Int, Int?>? {
    if (digits.length != 4 && digits.length != 8) return null
    val day = digits.substring(0, 2).toInt()
    val month = digits.substring(2, 4).toInt()
    val year = if (digits.length == 8) digits.substring(4).toInt() else null
    if (year != null && (year < 1900 || year > LocalDate.now().year)) return null
    return if (Birthdays.valid(day, month, year)) Triple(day, month, year) else null
}

/** Zeigt die getippten Ziffern als Datum mit Punkten: 14031948 → 14.03.1948. */
private object DateDots : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = buildString {
            for ((i, c) in d.withIndex()) {
                if (i == 2 || i == 4) append('.')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (if (offset > 2) 1 else 0) + (if (offset > 4) 1 else 0)
            override fun transformedToOriginal(offset: Int) =
                (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0)).coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
