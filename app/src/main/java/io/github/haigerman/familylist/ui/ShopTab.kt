package io.github.haigerman.familylist.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.ShopDelete
import io.github.haigerman.familylist.data.ShopDone
import io.github.haigerman.familylist.data.ShopItem
import io.github.haigerman.familylist.data.ShopPut
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.newId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopTab(data: FamilyData, myName: String) {
    var input by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShopItem?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun add() {
        val text = input.trim()
        if (text.isEmpty()) return
        Store.submit(ShopPut(ShopItem(newId(), text, false, myName)))
        input = ""
    }

    // neueste Einträge oben
    val open = data.shop.filter { !it.done }.asReversed()
    val done = data.shop.filter { it.done }.asReversed()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Was fehlt?") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Tastatur bleibt offen: so lassen sich mehrere Dinge nacheinander eintragen
                keyboardActions = KeyboardActions(onDone = { add() }),
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (input.isBlank()) Sand.copy(alpha = 0.3f) else Sand)
                    .clickable(enabled = input.isNotBlank(), role = Role.Button) { add() }
                    .semantics { contentDescription = "Auf die Einkaufsliste" },
                contentAlignment = Alignment.Center,
            ) { Text("＋", fontSize = 24.sp, color = OnSand) }
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
                if (open.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (done.isEmpty()) "Die Einkaufsliste ist leer.\nOben eintragen, was beim nächsten Einkauf mit soll – alle in der Familie sehen es."
                            else "Alles eingekauft 🎉",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                        )
                    }
                }
                items(open, key = { it.id }) { item ->
                    ShopRow(item, data.colorOf(item.by), Modifier.animateItem(), onToggle = { Store.submit(ShopDone(item.id, true)) }, onEdit = { editing = item })
                }
                if (done.isNotEmpty()) {
                    item(key = "done") {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Erledigt · ${done.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { Store.submit(ShopDelete(done.map { it.id })) }) { Text("Erledigte entfernen", color = Sand, fontSize = 13.sp) }
                        }
                    }
                    items(done, key = { it.id }) { item ->
                        ShopRow(item, data.colorOf(item.by), Modifier.animateItem(), onToggle = { Store.submit(ShopDone(item.id, false)) }, onEdit = { editing = item })
                    }
                }
            }
        }
    }

    editing?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Card,
            title = { Text("Eintrag ändern") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    colors = fieldColors(),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    if (text.trim() != item.text) Store.submit(ShopPut(item.copy(text = text.trim())))
                    editing = null
                }) { Text("Speichern", color = Sand) }
            },
            dismissButton = {
                TextButton(onClick = { Store.submit(ShopDelete(listOf(item.id))); editing = null }) { Text("Löschen", color = Coral) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopRow(item: ShopItem, authorColor: Int, modifier: Modifier, onToggle: () -> Unit, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card.copy(alpha = if (item.done) 0.45f else 1f))
            .combinedClickable(onClick = onToggle, onLongClick = onEdit, onLongClickLabel = "Ändern")
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(item.done, if (item.done) "Wieder auf die Liste" else "Abhaken", onClick = onToggle)
        Text(
            item.text, fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.done) 0.5f else 1f),
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        if (item.by.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                item.by, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                color = tagColor(authorColor).copy(alpha = if (item.done) 0.5f else 1f),
            )
        }
    }
}
