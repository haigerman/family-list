package io.github.haigerman.familylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.haigerman.familylist.data.Settings
import io.github.haigerman.familylist.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** So oft holt die geöffnete App den Stand aus dem Space. */
private const val REFRESH_MS = 20_000L

@Composable
fun MainScreen(openTasks: Int, onSettings: () -> Unit) {
    val config by Settings.config.collectAsStateWithLifecycle()
    val data by Store.data.collectAsStateWithLifecycle()
    val status by Store.status.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // solange die App offen ist, regelmäßig abgleichen
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                Store.sync()
                delay(REFRESH_MS)
            }
        }
    }
    LaunchedEffect(openTasks) { if (openTasks > 0) pager.scrollToPage(1) }

    val (statusText, statusBad) = when {
        !config.complete -> "OpenCloud ist noch nicht eingerichtet" to true
        status.error != null && status.pending > 0 ->
            "Kein Abgleich – ${if (status.pending == 1) "1 Änderung wartet" else "${status.pending} Änderungen warten"}" to true
        status.error != null -> status.error!! to true
        status.lastSync == 0L -> "Gleiche ab …" to false
        else -> "${config.spaceName.ifBlank { "Space" }} · Stand ${SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(status.lastSync))}" to false
    }

    Backdrop {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.statusBarsPadding().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Family List", fontSize = 30.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        statusText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (statusBad) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SymbolButton("⚙", "Einstellungen", onClick = onSettings)
            }

            val missing = when {
                config.name.isBlank() && !config.complete -> "Name und OpenCloud einrichten" to "Damit die Familie deine Einträge sieht"
                !config.hasLogin -> "OpenCloud einrichten" to "Adresse, Benutzername und App-Token fehlen noch"
                !config.complete -> "Space wählen" to "Der gemeinsame Ort für die Listen fehlt noch"
                config.name.isBlank() -> "Deinen Namen festlegen" to "Damit dir Tasks zugeordnet werden können"
                else -> null
            }
            if (missing != null) {
                Surface(
                    color = Card, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onSettings),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(missing.first, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(missing.second, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            val openItems = data.shop.count { !it.done }
            val openMine = data.tasks.count { !it.done }
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Card)
                    .padding(4.dp),
            ) {
                TabPill("Einkaufsliste", openItems, pager.currentPage == 0, Modifier.weight(1f)) { scope.launch { pager.animateScrollToPage(0) } }
                TabPill("Tasks", openMine, pager.currentPage == 1, Modifier.weight(1f)) { scope.launch { pager.animateScrollToPage(1) } }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                if (page == 0) ShopTab(data, config.name) else TasksTab(data, config.name)
            }
        }
    }
}

@Composable
private fun TabPill(label: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Sand else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 0) "$label · $count" else label, fontSize = 16.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) OnSand else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
