# Family List

Android-App für die Familie mit zwei Reitern: einer gemeinsamen **Einkaufsliste** und **Tasks**
mit Erinnerung. Alle Daten liegen als lesbare Markdown-Dateien in einem **OpenCloud-Space**, auf
den alle Familienmitglieder Zugriff haben – ein eigener Server oder ein Konto bei einem Dritten
ist nicht nötig.

## Einkaufsliste

- Oben eintragen, was fehlt, **＋** oder Enter – die Tastatur bleibt offen für den nächsten Eintrag.
- Alle sehen alle Einträge; am Eintrag steht, wer ihn geschrieben hat (in der Farbe seines Tags).
- Antippen hakt ab, noch einmal antippen holt den Eintrag zurück. Lange drücken: ändern oder löschen.
- **Erledigte entfernen** räumt die abgehakten Einträge weg.

## Tasks

- **＋ Neuer Task**: Titel, optional eine Notiz, **Für wen?** und eine **Erinnerung**.
- **Für wen?** – ein Task kann einem oder mehreren Familienmitgliedern zugeordnet werden
  (Tags antippen). Ohne Zuordnung geht er alle an.
- **Erinnerung** – „Heute 18:00“, „Morgen 09:00“ oder Datum und Uhrzeit frei wählen. Zur Zeit
  kommt eine Benachrichtigung, direkt mit dem Knopf **Erledigt**.
- Erinnert wird, wem der Task zugeordnet ist; Tasks ohne Zuordnung erinnern alle.
- **Alle / Meine** filtert die Liste; Tasks mit Zeit stehen oben, Überfälliges ist rot.

## Einrichtung (einmalig, auf jedem Handy)

In der App unter **⚙ Einstellungen**:

| Abschnitt | Inhalt |
|---|---|
| **Ich** | dein Name und die Farbe deines Tags |
| **OpenCloud** | Adresse (z. B. `cloud.example.com`), Benutzername und App-Token – wie in Lucid Scanner. Den Token erzeugst du in OpenCloud unter *Einstellungen → App-Tokens → Neu*. |
| **Space für die Listen** | **Space wählen** zeigt die Spaces, auf die du Zugriff hast. Alle wählen denselben. |

**Verbindung prüfen** zeigt, ob Anmeldung und Space stimmen. Den Space legt der Admin in
OpenCloud an und gibt allen Familienmitgliedern Vollzugriff; die App legt ihre Dateien beim
ersten Eintrag selbst an. Wer die App eingerichtet hat, erscheint von selbst in der
Familienliste und steht den anderen damit für Zuordnungen zur Wahl.

Der Token wird auf dem Gerät verschlüsselt gespeichert (Schlüssel im Android-Keystore) und ist
von Backups ausgenommen. Beim ersten Task mit Erinnerung fragt die App nach der Erlaubnis für
Benachrichtigungen.

## Die Dateien im Space

Drei Dateien liegen auf der obersten Ebene des Space:

`Einkaufsliste.md`
```markdown
# Einkaufsliste

- [ ] Milch (2 l) <!-- id:k3f9x2ab von:Anna -->
- [x] Brot <!-- id:p0q1r2s3 von:David -->
```

`Tasks.md`
```markdown
# Tasks

- [ ] Müll rausbringen @David @Mia ⏰ 2026-11-15 18:00 <!-- id:a1b2c3d4 von:David -->
  Gelbe Tonne nicht vergessen
- [x] Oma anrufen <!-- id:e5f6g7h8 von:Anna -->
```

`Familie.md`
```markdown
# Familie

- David (#E8C77A) <!-- id:m1n2o3p4 -->
- Anna (#FF8A65) <!-- id:q5r6s7t8 -->
```

- `@Name` ordnet einen Task zu (Leerzeichen im Namen werden zu `_`), `⏰ JJJJ-MM-TT HH:MM` ist die
  Erinnerungszeit, eingerückte Zeilen darunter sind die Notiz.
- Der Kommentar am Zeilenende trägt die Kennung des Eintrags (in der Markdown-Ansicht unsichtbar).
- Die Dateien lassen sich auch **von Hand** in OpenCloud bearbeiten: eine neue Zeile `- Butter`
  genügt, die App ergänzt Kästchen und Kennung beim nächsten Speichern. Überschriften und freier
  Text gehen dabei verloren – die Dateien gehören der App.

## Abgleich

- Jede Änderung ist sofort sichtbar und wird im Hintergrund in den Space geschrieben. Ohne Netz
  wartet sie auf dem Gerät („2 Änderungen warten“) und wird nachgereicht.
- Geschrieben wird nie blind: Die App holt den neuesten Stand, wendet nur die eigene Änderung
  darauf an („Milch abhaken“, „Eier ergänzen“) und speichert mit ETag-Prüfung. Kommt jemand
  dazwischen, wird neu gelesen und wiederholt – so überschreibt niemand die Einträge der anderen.
- Die geöffnete App gleicht alle 20 Sekunden ab (oder Liste nach unten ziehen), im Hintergrund
  etwa alle 15 Minuten. Darüber kommen auch Tasks der anderen samt Erinnerung aufs Handy.
- Erinnerungen stellt die App als Wecker im Gerät – es gibt keinen Push-Server. Eine Erinnerung,
  die ein anderer erst kurz vor ihrer Zeit anlegt, kann deshalb bis zum nächsten
  Hintergrund-Abgleich verspätet kommen. Verpasste Erinnerungen (Handy aus) werden bis zu
  24 Stunden nachgeholt.

Kommt eine Erinnerung nicht oder erst beim Öffnen der App an, bremst meist die Akku-Verwaltung
des Handys. Unter **⚙ Einstellungen → Erinnerungen** zeigt die App, ob Benachrichtigungen und
genaue Wecker erlaubt sind und ob sie vom Akkusparen ausgenommen ist – jeweils mit Knopf zum
Beheben. **Test: Erinnerung in 1 Minute** stellt einen Probe-Wecker auf genau dem Weg der echten
Erinnerungen; die Test-Benachrichtigung sagt, ob sie pünktlich kam. Manche Hersteller brauchen zusätzlich eine eigene Einstellung
(Details je Modell: https://dontkillmyapp.com).

## Aufbau

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt`, `App.kt` | Start der Oberfläche; Hintergrund-Abgleich und Wecker beim App-Start |
| `ui/MainScreen.kt` | Kopf mit Abgleich-Status, die beiden Reiter |
| `ui/ShopTab.kt` | Einkaufsliste |
| `ui/TasksTab.kt`, `ui/TaskEditor.kt` | Tasks und der Dialog zum Anlegen/Ändern |
| `ui/SettingsScreen.kt` | Name, Tag-Farbe, OpenCloud-Zugang, Space-Auswahl |
| `data/Markdown.kt` | Liest und schreibt die drei Markdown-Dateien |
| `data/Ops.kt` | Die Änderungen („abhaken“, „ergänzen“ …) und wie sie auf einen Stand angewendet werden |
| `data/Sync.kt`, `data/Store.kt` | Abgleich einer Datei mit ETag-Prüfung; Stand und Warteschlange auf dem Gerät |
| `data/Settings.kt` | Einstellungen, Token-Verschlüsselung |
| `cloud/OpenCloudClient.kt` | Spaces auflisten (Graph-API), Dateien per WebDAV lesen und schreiben |
| `notify/Reminders.kt` | Wecker stellen, Benachrichtigung zeigen, „Erledigt“ |
| `notify/SyncWorker.kt` | Abgleich im Hintergrund (WorkManager) |

Mindestens Android 8.0 (API 26), Ziel-API 36. Keine Google-Dienste nötig.

## Lokal bauen

Voraussetzungen: JDK 17+ und das Android SDK (Platform 36).

```bash
./gradlew :app:assembleDebug
```

Tests: `./gradlew :app:testDebugUnitTest` – geprüft werden das Markdown-Format, das
Zusammenführen von Änderungen, der Abgleich gegen einen nachgebauten OpenCloud-Server (auch wenn
jemand genau beim Speichern dazwischenkommt) und dass sich alle Bildschirme zeichnen lassen
(Bilder unter `app/build/screens/`).

## Release (Installation über Obtainium)

1. Einmalig Repository-Secrets anlegen (*Settings → Secrets and variables → Actions*):
   `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` –
   alternativ mit dem Präfix `SIGNING_` (`SIGNING_KEYSTORE_BASE64` …).
2. Die Versionsnummer in der Datei `VERSION` erhöhen und pushen. Der Workflow baut die signierte
   APK, legt den Tag `vX.Y.Z` an und hängt die APK an ein GitHub-Release.
3. In Obtainium: *App hinzufügen* → `https://github.com/haigerman/family-list`.

Der Versionscode wird aus der Version berechnet (`1.2.3` → 10203).
