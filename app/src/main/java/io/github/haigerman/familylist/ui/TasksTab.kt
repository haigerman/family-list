package io.github.haigerman.familylist.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.Task
import io.github.haigerman.familylist.data.TaskDelete
import io.github.haigerman.familylist.data.TaskDone
import io.github.haigerman.familylist.notify.Reminders
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** „Heute, 18:00“ · „Morgen, 09:00“ · „Sa., 15. Nov., 18:00“ */
fun dueLabel(due: LocalDateTime, today: LocalDate = LocalDate.now()): String {
    val time = due.format(DateTimeFormatter.ofPattern("HH:mm"))
    val day = when (due.toLocalDate()) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> due.format(DateTimeFormatter.ofPattern(if (due.year == today.year) "EEE, d. MMM" else "EEE, d. MMM yyyy", Locale.GERMAN))
    }
    return "$day, $time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTab(data: FamilyData, myName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    // der Task im Editor; ein neuer hat noch keine Kennung in der Liste
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    var canNotify by remember { mutableStateOf(Reminders.hasPermission(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Reminders.hasPermission(context)
        onPauseOrDispose { }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
        if (it) Reminders.refresh(context, Store.data.value)
    }

    val shown = if (onlyMine) data.tasks.filter { Reminders.concerns(it, myName) } else data.tasks
    // zuerst, was eine Zeit hat (das Nächste oben), dann der Rest – Neues oben
    val open = shown.filter { !it.done }.asReversed().sortedBy { it.due ?: LocalDateTime.MAX }
    val done = shown.filter { it.done }.asReversed()
    val now = LocalDateTime.now()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterPill("Alle", !onlyMine) { onlyMine = false }
            FilterPill("Meine", onlyMine) { onlyMine = true }
            Box(Modifier.weight(1f))
            PrimaryButton("＋ Neuer Task") { creating = true }
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
                if (!canNotify && data.tasks.any { !it.done && it.due != null }) {
                    item(key = "notify") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(CardOn)
                                .clickable { if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Benachrichtigungen erlauben", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Sonst kommen die Erinnerungen nicht an", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (open.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            when {
                                done.isNotEmpty() -> "Alles erledigt 🎉"
                                onlyMine -> "Für dich steht nichts an."
                                else -> "Noch keine Tasks.\nMit „Neuer Task“ legst du einen an – auf Wunsch mit Erinnerung und für bestimmte Familienmitglieder."
                            },
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                        )
                    }
                }
                items(open, key = { it.id }) { task ->
                    TaskRow(task, data, now, Modifier.animateItem(), onToggle = { Store.submit(TaskDone(task.id, true)) }, onOpen = { editing = task })
                }
                if (done.isNotEmpty()) {
                    item(key = "done") {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Erledigt · ${done.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { Store.submit(TaskDelete(done.map { it.id })) }) { Text("Erledigte entfernen", color = Sand, fontSize = 13.sp) }
                        }
                    }
                    items(done, key = { it.id }) { task ->
                        TaskRow(task, data, now, Modifier.animateItem(), onToggle = { Store.submit(TaskDone(task.id, false)) }, onOpen = { editing = task })
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        TaskEditor(
            task = editing, data = data, myName = myName,
            onDismiss = { creating = false; editing = null },
            onSavedWithReminder = {
                if (!canNotify && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) CardOn else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 15.sp, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(task: Task, data: FamilyData, now: LocalDateTime, modifier: Modifier, onToggle: () -> Unit, onOpen: () -> Unit) {
    val fade = if (task.done) 0.5f else 1f
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card.copy(alpha = if (task.done) 0.45f else 1f))
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(task.done, if (task.done) "Wieder öffnen" else "Erledigt", onClick = onToggle)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                task.title, fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade),
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            if (task.note.isNotBlank()) {
                Text(
                    task.note.lines().first(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade),
                )
            }
            if (task.due != null || task.assignees.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    task.due?.let { due ->
                        val late = !task.done && due.isBefore(now)
                        Text(
                            "⏰ " + dueLabel(due), fontSize = 13.sp, fontWeight = if (late) FontWeight.Medium else FontWeight.Normal,
                            color = (if (late) Coral else Mist).copy(alpha = fade),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    for (name in task.assignees) TagChip(name, data.colorOf(name), selected = !task.done)
                }
            }
        }
    }
}
