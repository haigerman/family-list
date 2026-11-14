package io.github.haigerman.familylist.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.haigerman.familylist.BuildConfig
import io.github.haigerman.familylist.cloud.OpenCloudClient
import io.github.haigerman.familylist.cloud.Space
import io.github.haigerman.familylist.data.Config
import io.github.haigerman.familylist.data.Settings
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.TAG_COLORS
import io.github.haigerman.familylist.data.cleanName
import io.github.haigerman.familylist.notify.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Eingaben erst lokal halten und sofort mitspeichern – so springt beim Tippen nichts
    var config by remember { mutableStateOf(Settings.config.value) }
    fun change(new: Config) {
        config = new
        Settings.update(new)
    }
    val data by Store.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf(config.name) }
    var showToken by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    // Ergebnis der Verbindungsprüfung: Text und ob es geklappt hat
    var testResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var chooseSpace by remember { mutableStateOf(false) }

    var canNotify by remember { mutableStateOf(Reminders.hasPermission(context)) }
    var exact by remember { mutableStateOf(Reminders.exactAllowed(context)) }
    var unrestricted by remember { mutableStateOf(Reminders.batteryUnrestricted(context)) }
    var lastFired by remember { mutableStateOf(Reminders.lastFired(context)) }
    var testStarted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        canNotify = Reminders.hasPermission(context)
        exact = Reminders.exactAllowed(context)
        unrestricted = Reminders.batteryUnrestricted(context)
        lastFired = Reminders.lastFired(context)
        onPauseOrDispose { }
    }
    fun open(action: String) {
        try {
            context.startActivity(Intent(action, Uri.parse("package:" + context.packageName)))
        } catch (_: Exception) {
            // manche Geräte kennen die Seite nicht: dann die App-Info zeigen
            runCatching { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }
        }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
        if (it) Reminders.refresh(context, Store.data.value)
    }

    /** Andere Zugangsdaten: der gewählte Space gehört womöglich nicht mehr dazu. */
    fun changeLogin(new: Config) {
        testResult = null
        change(new)
    }

    BackHandler(onBack = onBack)

    Backdrop {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.statusBarsPadding().padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SymbolButton("‹", "Zurück", onClick = onBack)
                Text("Einstellungen", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SectionLabel("Ich")
                Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it.take(30)
                                change(config.copy(name = cleanName(it)))
                            },
                            label = { Text("Dein Name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Hint("Mit diesem Namen stehst du an deinen Einträgen, und über ihn werden dir Tasks zugeordnet.")
                        Spacer(Modifier.height(16.dp))
                        Text("Farbe deines Tags", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (rgb in TAG_COLORS) {
                                val selected = config.color == rgb
                                Box(
                                    Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                        .padding(5.dp)
                                        .clip(CircleShape)
                                        .background(tagColor(rgb))
                                        .clickable(role = Role.RadioButton) { change(config.copy(color = rgb)) }
                                        .semantics { contentDescription = "Farbe #%06X".format(rgb) },
                                    contentAlignment = Alignment.Center,
                                ) { if (selected) Text("✓", fontSize = 16.sp, color = Night) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("So sehen dich die anderen:  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TagChip(config.name.ifBlank { "Name" }, config.color, large = true)
                        }
                    }
                }

                SectionLabel("OpenCloud")
                Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = config.serverUrl,
                            onValueChange = { changeLogin(config.copy(serverUrl = it.trim())) },
                            label = { Text("Adresse") },
                            placeholder = { Text("cloud.example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = config.user,
                            onValueChange = { changeLogin(config.copy(user = it.trim())) },
                            label = { Text("Benutzername") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email, imeAction = ImeAction.Next,
                                autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None,
                            ),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = config.token,
                            onValueChange = { changeLogin(config.copy(token = it.trim())) },
                            label = { Text("App-Token") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            trailingIcon = {
                                SymbolButton(if (showToken) "🙈" else "👁", if (showToken) "Token verbergen" else "Token anzeigen") { showToken = !showToken }
                            },
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Hint("Den Token erzeugst du in OpenCloud unter Einstellungen → App-Tokens. Er steht zusammen mit dem Benutzernamen für die Anmeldung und wird auf dem Gerät verschlüsselt gespeichert.")
                    }
                }

                SectionLabel("Space für die Listen")
                Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "In diesem Space liegen Einkaufsliste und Tasks der Familie. Alle wählen denselben Space; den Zugriff darauf vergibt der Admin in OpenCloud.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📁", fontSize = 20.sp)
                            Text(
                                config.spaceName.ifBlank { "Noch kein Space gewählt" }, fontSize = 17.sp,
                                color = if (config.spaceUrl.isBlank()) Coral else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlineButton(
                            if (config.spaceUrl.isBlank()) "Space wählen" else "Anderen Space wählen",
                            Modifier.fillMaxWidth(), enabled = config.hasLogin,
                        ) { chooseSpace = true }
                        Spacer(Modifier.height(10.dp))
                        OutlineButton(
                            if (testing) "Prüfe …" else "Verbindung prüfen",
                            Modifier.fillMaxWidth(),
                            enabled = config.hasLogin && !testing,
                        ) {
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = try {
                                    withContext(Dispatchers.IO) {
                                        val client = OpenCloudClient(config.serverUrl, config.user, config.token)
                                        if (config.spaceUrl.isBlank()) {
                                            client.listSpaces()
                                            "Anmeldung klappt. Jetzt noch den Space wählen."
                                        } else {
                                            client.checkSpace(config.spaceUrl)
                                            "Verbindung steht – „${config.spaceName}“ ist erreichbar."
                                        }
                                    } to true
                                } catch (e: Exception) {
                                    (e.message ?: "Verbindung fehlgeschlagen.") to false
                                }
                                testing = false
                            }
                        }
                        testResult?.let { (text, ok) ->
                            Text(
                                (if (ok) "✓  " else "") + text, fontSize = 14.sp, color = if (ok) Mist else Coral,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }

                if (data.members.isNotEmpty()) {
                    SectionLabel("Familie")
                    Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (m in data.members) TagChip(m.name, m.color, large = true)
                            }
                            Hint("Wer die App mit diesem Space eingerichtet hat, erscheint hier von selbst.")
                        }
                    }
                }

                SectionLabel("Erinnerungen")
                Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Check(canNotify, "Benachrichtigungen sind erlaubt", "Benachrichtigungen sind nicht erlaubt")
                        if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                            OutlineButton("Benachrichtigungen erlauben", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }
                        Check(exact, "Wecker auf die Minute genau sind erlaubt", "Genaue Wecker sind nicht erlaubt – Erinnerungen kommen verspätet")
                        if (!exact && Build.VERSION.SDK_INT >= 31) {
                            OutlineButton("Genaue Wecker erlauben", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) { open(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
                        }
                        Check(unrestricted, "Vom Akkusparen ausgenommen", "Das Handy darf die App zum Akkusparen bremsen – Erinnerungen können ausbleiben")
                        if (!unrestricted) {
                            OutlineButton("Vom Akkusparen ausnehmen", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) { open(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
                        }
                        Hint("Du wirst an Tasks erinnert, die dir oder niemandem zugeordnet sind. Tasks der anderen holt die App etwa alle 15 Minuten im Hintergrund ab.")
                        Spacer(Modifier.height(12.dp))
                        OutlineButton(if (testStarted) "Test läuft – kommt in 1 Minute" else "Test: Erinnerung in 1 Minute", Modifier.fillMaxWidth(), enabled = canNotify && !testStarted) {
                            Reminders.test(context)
                            testStarted = true
                        }
                        Hint(
                            "Antippen, die App schließen und das Handy sperren. Nach einer Minute sollten zwei Benachrichtigungen kommen: " +
                                "„Test A“ (so stellt die App ihre Erinnerungen) und „Test B“ (zum Vergleich)." +
                                if (lastFired.first == 0L) " Bisher hat auf diesem Gerät noch kein Wecker der App ausgelöst."
                                else " Zuletzt ausgelöst: ${java.text.SimpleDateFormat("d. MMM, HH:mm", java.util.Locale.GERMAN).format(java.util.Date(lastFired.first))}" +
                                    (if (lastFired.second >= 60_000) " (${lastFired.second / 60_000} Min. zu spät)." else " (pünktlich).")
                        )
                    }
                }

                Text(
                    "Family List ${BuildConfig.VERSION_NAME}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (chooseSpace) {
        SpaceChooser(
            config = config,
            onPick = { space ->
                change(config.copy(spaceId = space.id, spaceName = space.name, spaceUrl = space.webDavUrl))
                testResult = null
                chooseSpace = false
                Store.requestSync()
            },
            onDismiss = { chooseSpace = false },
        )
    }
}

/** Zeigt die Spaces, auf die der Benutzer in OpenCloud Zugriff hat. */
@Composable
private fun SpaceChooser(config: Config, onPick: (Space) -> Unit, onDismiss: () -> Unit) {
    var spaces by remember { mutableStateOf<List<Space>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            spaces = withContext(Dispatchers.IO) { OpenCloudClient(config.serverUrl, config.user, config.token).listSpaces() }
        } catch (e: Exception) {
            error = e.message ?: "Die Spaces ließen sich nicht lesen."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Space wählen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val list = spaces
                when {
                    error != null -> Text(error!!, color = Coral)
                    list == null -> Text("Lade Spaces …", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.isEmpty() -> Text(
                        "Du hast in dieser OpenCloud auf keinen Space Zugriff. Der Admin muss den Space anlegen und dich hinzufügen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> for (space in list) {
                        val current = space.webDavUrl == config.spaceUrl
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (current) CardOn else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f))
                                .clickable(role = Role.Button) { onPick(space) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📁", fontSize = 18.sp)
                            Text(space.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            if (current) Text("✓", color = Sand, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

@Composable
private fun Check(ok: Boolean, good: String, bad: String) {
    Text(if (ok) "✓  $good" else "✕  $bad", fontSize = 15.sp, color = if (ok) Mist else Coral, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}
