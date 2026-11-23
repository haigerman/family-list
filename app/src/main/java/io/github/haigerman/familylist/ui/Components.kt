package io.github.haigerman.familylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hintergrund aller Bildschirme: Verlauf von Nachtblau nach dunklem Petrol. */
@Composable
fun Backdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Night, DeepTeal))),
    ) { content() }
}

@Composable
fun Header(title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

/** Runder Knopf mit einem Zeichen als Symbol (wie in den anderen Apps: keine Icon-Bibliothek). */
@Composable
fun SymbolButton(symbol: String, description: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 22.sp, color = color.copy(alpha = if (enabled) 1f else 0.3f))
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Sand else Sand.copy(alpha = 0.3f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OnSand, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun OutlineButton(label: String, modifier: Modifier = Modifier, color: Color = Sand, enabled: Boolean = true, onClick: () -> Unit) {
    val c = color.copy(alpha = if (enabled) 1f else 0.3f)
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, c.copy(alpha = c.alpha * 0.6f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, color = c, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Sand,
    focusedLabelColor = Sand,
    cursorColor = Sand,
)

fun tagColor(rgb: Int): Color = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))

/** Der Tag eines Familienmitglieds: Name in seiner Farbe. */
@Composable
fun TagChip(name: String, rgb: Int, modifier: Modifier = Modifier, selected: Boolean = true, large: Boolean = false, onClick: (() -> Unit)? = null) {
    val color = tagColor(rgb)
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (selected) 0.9f else 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Checkbox, onClick = onClick) else Modifier)
            .padding(horizontal = if (large) 14.dp else 9.dp, vertical = if (large) 9.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name, fontSize = if (large) 15.sp else 12.sp, maxLines = 1,
            color = if (selected) color else color.copy(alpha = 0.6f),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** Runder Haken zum Abhaken von Einträgen. */
@Composable
fun CheckCircle(checked: Boolean, description: String, modifier: Modifier = Modifier, color: Color = Sand, onClick: () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) color.copy(alpha = 0.55f) else Color.Transparent)
                .border(2.dp, color.copy(alpha = if (checked) 0.55f else 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Night)
        }
    }
}

@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp))
}

/**
 * Die aktuelle Zeit als Zustand: Compose baut sonst nichts neu, nur weil die Uhr weiterläuft –
 * „Morgen, 07:00“ stünde am nächsten Tag noch da. Läuft jede volle Minute weiter und springt
 * sofort, wenn die App wieder in den Vordergrund kommt (etwa am nächsten Morgen).
 */
@Composable
fun rememberNow(): java.time.LocalDateTime {
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(java.time.LocalDateTime.now()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                now = java.time.LocalDateTime.now()
                val ms = now.toLocalTime().let { (60 - it.second) * 1000L - it.nano / 1_000_000 }
                kotlinx.coroutines.delay(ms.coerceIn(200L, 60_000L))
            }
        }
    }
    return now
}
