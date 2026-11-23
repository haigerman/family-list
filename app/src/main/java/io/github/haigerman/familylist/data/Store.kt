package io.github.haigerman.familylist.data

import android.content.Context
import android.content.SharedPreferences
import io.github.haigerman.familylist.cloud.OpenCloudClient
import io.github.haigerman.familylist.notify.Reminders
import io.github.haigerman.familylist.notify.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class SyncStatus(
    val syncing: Boolean = false,
    /** Zeitpunkt des letzten gelungenen Abgleichs (0: noch nie). */
    val lastSync: Long = 0,
    val error: String? = null,
    /** Änderungen, die noch nicht im Space angekommen sind. */
    val pending: Int = 0,
)

/**
 * Die Listen der Familie: der zuletzt aus dem Space gelesene Stand plus die eigenen Änderungen,
 * die noch auf den Abgleich warten. Beides liegt auch auf dem Gerät, die App funktioniert also
 * ohne Netz weiter.
 */
object Store {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val lock = Any()
    private val cache = HashMap<Doc, DocState>()
    private var pending: List<Op> = emptyList()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _data = MutableStateFlow(FamilyData())
    val data: StateFlow<FamilyData> = _data
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        Settings.init(context)
        prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE)
        synchronized(lock) {
            if (prefs.getString("cacheSpace", "") == Settings.config.value.spaceUrl) {
                for (doc in Doc.entries) {
                    val file = cacheFile(doc)
                    if (file.exists()) cache[doc] = DocState(file.readText(), prefs.getString("etag_${doc.name}", null))
                }
            }
            pending = OpCodec.decode(prefs.getString("pending", null))
        }
        _status.value = SyncStatus(lastSync = prefs.getLong("lastSync", 0), pending = pending.size)
        publish()
    }

    /** Eine eigene Änderung: sofort sichtbar, wird im Hintergrund in den Space geschrieben. */
    fun submit(vararg ops: Op) {
        if (ops.isEmpty()) return
        synchronized(lock) {
            pending = pending + ops
            savePending()
        }
        publish()
        Reminders.refresh(context, _data.value)
        requestSync()
    }

    /** Abgleich anstoßen, ohne auf das Ergebnis zu warten. */
    fun requestSync() {
        scope.launch {
            // klappt es gerade nicht (kein Netz), holt es der Hintergrund-Abgleich nach
            if (!sync() && synchronized(lock) { pending.isNotEmpty() }) SyncWorker.kick(context)
        }
    }

    /** Gleicht alle Dateien mit dem Space ab. Liefert, ob es geklappt hat. */
    suspend fun sync(): Boolean = mutex.withLock {
        val config = Settings.config.value
        if (!config.complete) return@withLock false
        _status.value = _status.value.copy(syncing = true)
        val error = withContext(Dispatchers.IO) {
            try {
                syncAll(config)
                null
            } catch (e: Exception) {
                e.message ?: "Abgleich fehlgeschlagen."
            }
        }
        val now = System.currentTimeMillis()
        if (error == null) prefs.edit().putLong("lastSync", now).apply()
        _status.value = _status.value.copy(
            syncing = false, error = error, lastSync = if (error == null) now else _status.value.lastSync,
        )
        publish()
        Reminders.refresh(context, _data.value)
        error == null
    }

    private fun syncAll(config: Config) {
        synchronized(lock) {
            // anderer Space gewählt: der gemerkte Stand gehört nicht mehr dazu
            if (prefs.getString("cacheSpace", "") != config.spaceUrl) {
                cache.clear()
                Doc.entries.forEach { cacheFile(it).delete() }
                prefs.edit().putString("cacheSpace", config.spaceUrl).apply { Doc.entries.forEach { remove("etag_${it.name}") } }.apply()
            }
            // Namen geändert: die eigenen Zuordnungen ziehen mit
            val published = prefs.getString("publishedName", "").orEmpty()
            if (config.name.isNotBlank() && published != config.name) {
                if (published.isNotBlank()) pending = pending + TaskRetag(published, config.name)
                savePending()
                prefs.edit().putString("publishedName", config.name).apply()
            }
        }
        val client = OpenCloudClient(config.serverUrl, config.user, config.token)
        val me = if (config.name.isBlank()) null else Member(config.memberId, config.name, config.color)
        for (doc in Doc.entries.sortedBy { it != Doc.FAMILY }) {
            val ops = synchronized(lock) { pending.filter { it.doc == doc } }
            val state = Sync.syncDoc(client, config.spaceUrl, doc, synchronized(lock) { cache[doc] }, ops) { remote ->
                // sich selbst in die Familienliste eintragen bzw. Name und Farbe aktuell halten
                if (doc == Doc.FAMILY && me != null && me !in remote.members) listOf(MemberPut(me)) else emptyList()
            }
            synchronized(lock) {
                cache[doc] = state
                pending = pending.filterNot { p -> ops.any { it === p } }
                savePending()
                if (state.text == null) cacheFile(doc).delete() else cacheFile(doc).writeText(state.text)
                prefs.edit().putString("etag_${doc.name}", state.etag).apply()
            }
        }
    }

    private fun cacheFile(doc: Doc) = File(context.filesDir, doc.file)

    private fun savePending() {
        prefs.edit().putString("pending", OpCodec.encode(pending)).apply()
    }

    private fun publish() {
        val (data, count) = synchronized(lock) {
            val base = FamilyData(
                shop = Markdown.parseShop(cache[Doc.SHOP]?.text.orEmpty()),
                tasks = Markdown.parseTasks(cache[Doc.TASKS]?.text.orEmpty()),
                members = Markdown.parseFamily(cache[Doc.FAMILY]?.text.orEmpty()),
                birthdays = Markdown.parseBirthdays(cache[Doc.BIRTHDAYS]?.text.orEmpty()),
            )
            base.apply(pending) to pending.size
        }
        _data.value = data
        _status.value = _status.value.copy(pending = count)
    }
}
