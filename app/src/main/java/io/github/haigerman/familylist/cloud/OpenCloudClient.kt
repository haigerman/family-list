package io.github.haigerman.familylist.cloud

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

open class OpenCloudException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Die Datei wurde inzwischen von jemand anderem geändert – neu lesen und noch einmal versuchen. */
class ConflictException : OpenCloudException("Die Datei wurde gerade von jemand anderem geändert.")

/** Ein Space, wie ihn OpenCloud meldet. */
data class Space(val id: String, val name: String, val webDavUrl: String)

/** Ergebnis von [OpenCloudClient.read]. */
class Fetched(
    /** Inhalt; null, wenn es die Datei noch nicht gibt oder sie unverändert ist. */
    val text: String?,
    val etag: String?,
    /** Die Datei ist seit dem bekannten Stand unverändert. */
    val notModified: Boolean = false,
)

/**
 * Liest und schreibt Textdateien per WebDAV in einem OpenCloud-Space. Anmeldung mit
 * Benutzername und App-Token (in OpenCloud unter Einstellungen → App-Tokens erzeugt).
 */
class OpenCloudClient(
    serverUrl: String,
    user: String,
    token: String,
    private val http: OkHttpClient = defaultClient,
) {
    private val base: HttpUrl = parseServerUrl(serverUrl)
        ?: throw OpenCloudException("Die OpenCloud-Adresse ist ungültig.")
    private val auth = Credentials.basic(user, token, Charsets.UTF_8)

    /** Die Spaces (Projekt-Bereiche), auf die der Benutzer Zugriff hat – alphabetisch. */
    fun listSpaces(): List<Space> {
        val url = base.newBuilder().addPathSegments("graph/v1.0/me/drives").build()
        execute(Request.Builder().url(url).get()).use { r ->
            if (r.code != 200) throw failure(r.code, "Spaces lesen")
            val drives = try {
                JSONObject(r.body?.string().orEmpty()).optJSONArray("value")
            } catch (e: Exception) {
                throw OpenCloudException("Die Antwort der OpenCloud ließ sich nicht lesen.", e)
            }
            val spaces = ArrayList<Space>()
            for (i in 0 until (drives?.length() ?: 0)) {
                val drive = drives!!.optJSONObject(i) ?: continue
                if (drive.optString("driveType") != "project") continue
                val root = drive.optJSONObject("root") ?: continue
                // deaktivierte Spaces tragen einen „deleted“-Vermerk
                if (root.has("deleted")) continue
                val dav = root.optString("webDavUrl").trimEnd('/')
                if (dav.toHttpUrlOrNull() == null) continue
                spaces += Space(drive.optString("id"), drive.optString("name").ifBlank { "Space" }, dav)
            }
            val collator = java.text.Collator.getInstance(java.util.Locale.GERMAN)
            return spaces.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
    }

    /** Prüft, ob der Space erreichbar ist. */
    fun checkSpace(spaceUrl: String) {
        execute(Request.Builder().url(spaceRoot(spaceUrl)).method("PROPFIND", null).header("Depth", "0")).use { r ->
            if (r.code != 207 && r.code != 200) throw failure(r.code, "Verbindung")
        }
    }

    /**
     * Liest die Datei [name] aus dem Space. Mit [knownEtag] antwortet OpenCloud nur mit dem
     * Inhalt, wenn sich die Datei seither geändert hat.
     */
    fun read(spaceUrl: String, name: String, knownEtag: String? = null): Fetched {
        val request = Request.Builder().url(file(spaceUrl, name)).get()
        if (knownEtag != null) request.header("If-None-Match", knownEtag)
        execute(request).use { r ->
            return when (r.code) {
                200 -> Fetched(r.body?.string().orEmpty(), etagOf(r))
                304 -> Fetched(null, knownEtag, notModified = true)
                404 -> Fetched(null, null)
                else -> throw failure(r.code, "Lesen")
            }
        }
    }

    /**
     * Schreibt die Datei [name]. [etag] ist der Stand, auf dem die Änderung beruht; [create] heißt,
     * die Datei gab es noch nicht. Hat sie sich inzwischen geändert (oder hat sie inzwischen jemand
     * angelegt), gibt es eine [ConflictException]. Liefert den neuen Stand.
     */
    fun write(spaceUrl: String, name: String, text: String, etag: String?, create: Boolean): String? {
        val body = text.toByteArray(Charsets.UTF_8).toRequestBody("text/markdown; charset=utf-8".toMediaType())
        val request = Request.Builder().url(file(spaceUrl, name)).put(body)
        if (create) request.header("If-None-Match", "*") else if (etag != null) request.header("If-Match", etag)
        execute(request).use { r ->
            return when (r.code) {
                200, 201, 204 -> etagOf(r)
                412 -> throw ConflictException()
                423 -> throw OpenCloudException("Die Datei ist in OpenCloud gerade gesperrt (wird sie dort bearbeitet?).")
                else -> throw failure(r.code, "Speichern")
            }
        }
    }

    // ------------------------------------------------------------------

    private fun etagOf(r: okhttp3.Response): String? = (r.header("ETag") ?: r.header("OC-ETag"))?.takeIf { it.isNotBlank() }

    private fun spaceRoot(spaceUrl: String): HttpUrl =
        spaceUrl.trimEnd('/').toHttpUrlOrNull() ?: throw OpenCloudException("Die Adresse des Space ist ungültig.")

    private fun file(spaceUrl: String, name: String): HttpUrl = spaceRoot(spaceUrl).newBuilder().addPathSegment(name).build()

    private fun execute(builder: Request.Builder): okhttp3.Response {
        try {
            return http.newCall(builder.header("Authorization", auth).build()).execute()
        } catch (e: UnknownHostException) {
            throw OpenCloudException("OpenCloud nicht gefunden – Adresse und Internetverbindung prüfen.", e)
        } catch (e: SSLException) {
            throw OpenCloudException("Keine sichere Verbindung zur OpenCloud möglich (${e.message}).", e)
        } catch (e: IOException) {
            throw OpenCloudException("OpenCloud nicht erreichbar (${e.message ?: e.javaClass.simpleName}).", e)
        }
    }

    private fun failure(code: Int, action: String): OpenCloudException = OpenCloudException(
        when (code) {
            401 -> "Anmeldung abgelehnt – Benutzername und App-Token prüfen."
            403 -> "Kein Zugriff auf diesen Space in OpenCloud."
            404 -> "Unter dieser Adresse wurde keine OpenCloud gefunden."
            507 -> "Der Speicherplatz in OpenCloud ist voll."
            else -> "$action fehlgeschlagen (HTTP $code)."
        }
    )

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /** Ergänzt `https://`, wenn kein Schema angegeben ist, und entfernt Schrägstriche am Ende. */
        fun parseServerUrl(input: String): HttpUrl? {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "https://$s"
            return s.toHttpUrlOrNull()
        }
    }
}
