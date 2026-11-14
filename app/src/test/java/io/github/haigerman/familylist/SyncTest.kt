package io.github.haigerman.familylist

import io.github.haigerman.familylist.cloud.OpenCloudClient
import io.github.haigerman.familylist.cloud.OpenCloudException
import io.github.haigerman.familylist.data.Doc
import io.github.haigerman.familylist.data.DocState
import io.github.haigerman.familylist.data.Markdown
import io.github.haigerman.familylist.data.Member
import io.github.haigerman.familylist.data.MemberPut
import io.github.haigerman.familylist.data.ShopDone
import io.github.haigerman.familylist.data.ShopItem
import io.github.haigerman.familylist.data.ShopPut
import io.github.haigerman.familylist.data.Sync
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

/** Kleiner WebDAV-Server, der sich wie OpenCloud verhält (Graph-Abfrage, GET/PUT mit ETag). */
class SyncTest {
    private val server = MockWebServer()
    private val log = ArrayList<String>()
    private val files = HashMap<String, String>()
    private val versions = HashMap<String, Int>()
    /** Läuft unmittelbar vor dem nächsten PUT: jemand anderes kommt dazwischen. */
    private var beforePut: (() -> Unit)? = null
    private val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("anna:geheim-token".toByteArray())

    private val url get() = "http://127.0.0.1:${server.port}"
    private val space get() = "$url/dav/spaces/abc\$123"
    private fun client() = OpenCloudClient(url, "anna", "geheim-token")

    private fun etag(name: String) = "\"v${versions[name]}\""
    private fun serverPut(name: String, text: String) {
        files[name] = text
        versions[name] = (versions[name] ?: 0) + 1
    }

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = synchronized(files) {
                val path = request.requestUrl!!.encodedPath
                log += "${request.method} $path"
                fun reply(code: Int, text: String = "") = MockResponse().setResponseCode(code).setBody(text)
                if (request.getHeader("Authorization") != expectedAuth) return reply(401)
                if (path == "/graph/v1.0/me/drives") {
                    return reply(200, """{"value":[
                        {"id":"p","name":"Anna","driveType":"personal","root":{"webDavUrl":"$url/dav/spaces/p"}},
                        {"id":"z","name":"Zuhause","driveType":"project","root":{"webDavUrl":"$url/dav/spaces/z/"}},
                        {"id":"f","name":"Familie","driveType":"project","root":{"webDavUrl":"$url/dav/spaces/abc${'$'}123"}},
                        {"id":"x","name":"Aus","driveType":"project","root":{"webDavUrl":"$url/dav/spaces/x","deleted":{"state":"trashed"}}}]}""")
                }
                val prefix = "/dav/spaces/abc\$123/"
                if (!path.startsWith(prefix)) return reply(404)
                val name = path.removePrefix(prefix)
                when (request.method) {
                    "GET" -> when {
                        name !in files -> reply(404)
                        request.getHeader("If-None-Match") == etag(name) -> reply(304)
                        else -> reply(200, files.getValue(name)).setHeader("ETag", etag(name))
                    }
                    "PUT" -> {
                        beforePut?.also { beforePut = null }?.invoke()
                        val ifMatch = request.getHeader("If-Match")
                        when {
                            request.getHeader("If-None-Match") == "*" && name in files -> reply(412)
                            ifMatch != null && (name !in files || ifMatch != etag(name)) -> reply(412)
                            else -> {
                                val created = name !in files
                                serverPut(name, request.body.readUtf8())
                                reply(if (created) 201 else 204).setHeader("ETag", etag(name))
                            }
                        }
                    }
                    else -> reply(400)
                }
            }
        }
        server.start()
    }

    @After fun stop() = server.shutdown()

    @Test fun listsOnlyUsableSpaces() {
        val spaces = client().listSpaces()
        assertEquals(listOf("Familie", "Zuhause"), spaces.map { it.name })
        assertEquals(space, spaces[0].webDavUrl)
        assertEquals("$url/dav/spaces/z", spaces[1].webDavUrl)
    }

    @Test fun wrongTokenIsExplained() {
        try {
            OpenCloudClient(url, "anna", "falsch").listSpaces()
            fail()
        } catch (e: OpenCloudException) {
            assertTrue(e.message!!.contains("Anmeldung abgelehnt"))
        }
    }

    @Test fun createsFileOnFirstChange() {
        val state = Sync.syncDoc(client(), space, Doc.SHOP, null, listOf(ShopPut(ShopItem("a", "Milch", false, "Anna"))))
        assertEquals("# Einkaufsliste\n\n- [ ] Milch <!-- id:a von:Anna -->\n", files["Einkaufsliste.md"])
        assertEquals(files["Einkaufsliste.md"], state.text)
        assertEquals("\"v1\"", state.etag)
    }

    @Test fun nothingToDoMeansNoWrite() {
        val empty = Sync.syncDoc(client(), space, Doc.TASKS, null, emptyList())
        assertNull(empty.text)
        serverPut("Tasks.md", "- [ ] von Hand geschrieben\n")
        val first = Sync.syncDoc(client(), space, Doc.TASKS, empty, emptyList())
        assertEquals("- [ ] von Hand geschrieben\n", first.text)
        log.clear()
        // unverändert: der Server schickt den Inhalt nicht noch einmal
        val second = Sync.syncDoc(client(), space, Doc.TASKS, first, emptyList())
        assertEquals(first.text, second.text)
        assertEquals(first.etag, second.etag)
        assertEquals(1, log.size)
        assertEquals("- [ ] von Hand geschrieben\n", files["Tasks.md"])
    }

    @Test fun mergesWithChangesOfOthers() {
        serverPut("Einkaufsliste.md", Markdown.writeShop(listOf(ShopItem("a", "Milch"), ShopItem("b", "Brot"))))
        val cached = Sync.syncDoc(client(), space, Doc.SHOP, null, emptyList())
        // inzwischen hat jemand anderes Eier ergänzt …
        serverPut("Einkaufsliste.md", Markdown.writeShop(listOf(ShopItem("a", "Milch"), ShopItem("b", "Brot"), ShopItem("c", "Eier"))))
        // … und noch jemand kommt genau beim Speichern dazwischen
        beforePut = { serverPut("Einkaufsliste.md", files.getValue("Einkaufsliste.md") + "- [ ] Käse <!-- id:d -->\n") }
        val state = Sync.syncDoc(client(), space, Doc.SHOP, cached, listOf(ShopDone("a", true), ShopPut(ShopItem("e", "Salz"))))
        assertEquals(
            listOf(ShopItem("a", "Milch", true), ShopItem("b", "Brot"), ShopItem("c", "Eier"), ShopItem("d", "Käse"), ShopItem("e", "Salz")),
            Markdown.parseShop(files.getValue("Einkaufsliste.md")),
        )
        assertEquals(files["Einkaufsliste.md"], state.text)
        assertEquals(etag("Einkaufsliste.md"), state.etag)
    }

    @Test fun someoneElseCreatesTheFileFirst() {
        beforePut = { serverPut("Einkaufsliste.md", "- [ ] Käse <!-- id:d -->\n") }
        Sync.syncDoc(client(), space, Doc.SHOP, null, listOf(ShopPut(ShopItem("e", "Salz"))))
        assertEquals(listOf("Käse", "Salz"), Markdown.parseShop(files.getValue("Einkaufsliste.md")).map { it.text })
    }

    @Test fun registersItselfInFamilyOnlyWhenNeeded() {
        val me = Member("m1", "Anna", 0xFF8A65)
        val auto = { remote: io.github.haigerman.familylist.data.FamilyData ->
            if (me !in remote.members) listOf(MemberPut(me)) else emptyList()
        }
        serverPut("Familie.md", "# Familie\n\n- David (#E8C77A) <!-- id:m0 -->\n")
        val state: DocState = Sync.syncDoc(client(), space, Doc.FAMILY, null, emptyList(), auto)
        assertEquals("# Familie\n\n- David (#E8C77A) <!-- id:m0 -->\n- Anna (#FF8A65) <!-- id:m1 -->\n", files["Familie.md"])
        log.clear()
        Sync.syncDoc(client(), space, Doc.FAMILY, state, emptyList(), auto)
        assertEquals(listOf("GET /dav/spaces/abc\$123/Familie.md"), log)
    }
}
