package io.github.haigerman.familylist.data

import io.github.haigerman.familylist.cloud.ConflictException
import io.github.haigerman.familylist.cloud.OpenCloudClient
import io.github.haigerman.familylist.cloud.OpenCloudException

/** Zuletzt gesehener Stand einer Datei: Inhalt (null: gibt es noch nicht) und ETag. */
class DocState(val text: String?, val etag: String?)

object Sync {
    fun parse(doc: Doc, text: String?): FamilyData = when (doc) {
        Doc.SHOP -> FamilyData(shop = Markdown.parseShop(text.orEmpty()))
        Doc.TASKS -> FamilyData(tasks = Markdown.parseTasks(text.orEmpty()))
        Doc.FAMILY -> FamilyData(members = Markdown.parseFamily(text.orEmpty()))
        Doc.BIRTHDAYS -> FamilyData(birthdays = Markdown.parseBirthdays(text.orEmpty()))
    }

    fun write(doc: Doc, data: FamilyData): String = when (doc) {
        Doc.SHOP -> Markdown.writeShop(data.shop)
        Doc.TASKS -> Markdown.writeTasks(data.tasks)
        Doc.FAMILY -> Markdown.writeFamily(data.members)
        Doc.BIRTHDAYS -> Markdown.writeBirthdays(data.birthdays)
    }

    /**
     * Gleicht eine Datei ab: neuesten Stand holen, die wartenden Änderungen [ops] darauf anwenden
     * und zurückschreiben. Kommt jemand anderes dazwischen, wird neu gelesen und wiederholt –
     * so überschreibt niemand die Einträge der anderen.
     *
     * [auto] liefert Änderungen, die sich erst aus dem Stand im Space ergeben.
     */
    fun syncDoc(
        client: OpenCloudClient,
        spaceUrl: String,
        doc: Doc,
        cached: DocState?,
        ops: List<Op>,
        auto: (FamilyData) -> List<Op> = { emptyList() },
    ): DocState {
        var known = cached
        repeat(5) {
            val fetched = client.read(spaceUrl, doc.file, known?.takeIf { it.text != null }?.etag)
            val text = if (fetched.notModified) known?.text else fetched.text
            val remote = parse(doc, text)
            val all = ops + auto(remote)
            val merged = remote.apply(all)
            if (merged == remote) return DocState(text, fetched.etag)
            val newText = write(doc, merged)
            try {
                val etag = client.write(spaceUrl, doc.file, newText, fetched.etag, create = text == null)
                return DocState(newText, etag)
            } catch (_: ConflictException) {
                known = null
            }
        }
        throw OpenCloudException("${doc.file} wird gerade von zu vielen gleichzeitig geändert – gleich noch einmal versuchen.")
    }
}
