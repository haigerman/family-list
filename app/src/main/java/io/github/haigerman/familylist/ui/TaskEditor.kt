package io.github.haigerman.familylist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.Task
import io.github.haigerman.familylist.data.TaskDelete
import io.github.haigerman.familylist.data.TaskPut
import io.github.haigerman.familylist.data.newId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Vorlauf, den eine Erinnerung für andere mindestens braucht. */
private const val MIN_LEAD_MINUTES = 10L

/** Legt einen Task an ([task] ist null) oder ändert ihn. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(task: Task?, data: FamilyData, myName: String, onDismiss: () -> Unit, onSavedWithReminder: () -> Unit) {
    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    var note by remember { mutableStateOf(task?.note.orEmpty()) }
    var assignees by remember { mutableStateOf(task?.assignees ?: emptyList()) }
    var due by remember { mutableStateOf(task?.due) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    // zur Wahl stehen alle aus der Familienliste, man selbst und wer schon zugeordnet ist
    val names = (data.members.map { it.name } + listOfNotNull(myName.takeIf { it.isNotBlank() }) + assignees).distinctBy { it.lowercase() }

    // Die Handys der anderen holen neue Tasks nur alle paar Minuten ab. Eine Erinnerung für andere
    // braucht deshalb Vorlauf, sonst ist ihre Zeit vorbei, bevor sie dort ankommt.
    val forOthers = assignees.any { !it.equals(myName, ignoreCase = true) }
    val changed = due != task?.due || assignees != (task?.assignees ?: emptyList<String>())
    val clock = rememberNow()
    val tooSoon = forOthers && changed && task?.done != true &&
        due?.isBefore(clock.plusMinutes(MIN_LEAD_MINUTES)) == true

    fun save() {
        val result = Task(
            id = task?.id ?: newId(), title = title.trim(), done = task?.done ?: false,
            assignees = assignees, due = due, note = note.trim(), by = task?.by ?: myName,
        )
        if (result != task) Store.submit(TaskPut(result))
        if (due != null && due != task?.due) onSavedWithReminder()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Card, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).fillMaxWidth().imePadding(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(if (task == null) "Neuer Task" else "Task", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Was ist zu tun?") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    maxLines = 3, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 4, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )

                EditorLabel("Für wen?")
                if (names.isEmpty()) {
                    Text(
                        "Noch niemand in der Familienliste. Lege in den Einstellungen deinen Namen fest.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (name in names) {
                            val selected = assignees.any { it.equals(name, ignoreCase = true) }
                            TagChip(name, data.colorOf(name), selected = selected, large = true) {
                                assignees = if (selected) assignees.filterNot { it.equals(name, ignoreCase = true) } else assignees + name
                            }
                        }
                    }
                    Text(
                        if (assignees.isEmpty()) "Niemand gewählt: der Task geht alle an." else "Nur die Gewählten werden erinnert.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }

                EditorLabel("Erinnerung")
                val current = due
                if (current == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val now = LocalDateTime.now()
                        val evening = LocalDate.now().atTime(18, 0)
                        if (evening.isAfter(now)) SmallOutline("Heute 18:00") { due = evening }
                        SmallOutline("Morgen 09:00") { due = LocalDate.now().plusDays(1).atTime(9, 0) }
                        SmallOutline("Datum & Zeit …") {
                            due = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                            pickDate = true
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallOutline(current.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN))) { pickDate = true }
                        Spacer(Modifier.width(8.dp))
                        SmallOutline(current.format(DateTimeFormatter.ofPattern("HH:mm")) + " Uhr") { pickTime = true }
                        Spacer(Modifier.weight(1f))
                        SymbolButton("✕", "Erinnerung entfernen") { due = null }
                    }
                    if (tooSoon) {
                        Text(
                            "Erinnerungen für andere müssen mindestens $MIN_LEAD_MINUTES Minuten in der Zukunft liegen – " +
                                "so lange kann es dauern, bis der Task auf ihrem Handy ankommt.",
                            fontSize = 12.sp, color = Coral, modifier = Modifier.padding(top = 6.dp),
                        )
                    } else if (current.isBefore(clock) && task?.done != true) {
                        Text("Diese Zeit ist schon vorbei.", fontSize = 12.sp, color = Coral, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task != null) {
                        TextButton(onClick = { Store.submit(TaskDelete(listOf(task.id))); onDismiss() }) { Text("Löschen", color = Coral) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.width(4.dp))
                    PrimaryButton("Speichern", enabled = title.isNotBlank() && !tooSoon) { save() }
                }
            }
        }
    }

    if (pickDate) {
        val base = due ?: LocalDateTime.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = base.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            colors = DatePickerDefaults.colors(containerColor = Card),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        due = LocalDateTime.of(day, base.toLocalTime())
                    }
                    pickDate = false
                    // bei einer neuen Erinnerung gleich weiter zur Uhrzeit
                    if (task?.due == null || task.due != due) pickTime = true
                }) { Text("Weiter", color = Sand) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Card), showModeToggle = false)
        }
    }
    if (pickTime) {
        val base = due ?: LocalDateTime.now()
        val state = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = Card,
            text = { TimePicker(state = state, colors = TimePickerDefaults.colors(clockDialColor = CardOn, timeSelectorSelectedContainerColor = CardOn, timeSelectorUnselectedContainerColor = Night)) },
            confirmButton = {
                TextButton(onClick = {
                    due = LocalDateTime.of(base.toLocalDate(), LocalTime.of(state.hour, state.minute))
                    pickTime = false
                }) { Text("Übernehmen", color = Sand) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun SmallOutline(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Sand.copy(alpha = 0.6f)),
    ) {
        Text(label, fontSize = 15.sp, color = Sand, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}
