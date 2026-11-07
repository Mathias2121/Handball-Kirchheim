# Kirchheim Handball (Android)

Zeigt für die SG HD-Kirchheim (Männer Bezirksklasse Staffel 2 Rhein-Neckar, Saison 2026/27):
Tabelle · Gesamtspielplan · Spielplan Kirchheim · Torschützenliste (aus den PDF-Spielberichten).

Die App holt Tabelle und Spielplan beim Handball4All-Dienst (derselbe, den die BWHV-Seite nutzt) und liest neue
PDF-Spielberichte direkt auf dem Handy aus. Elf bereits ausgewertete Spiele sind als Startdaten
(`app/src/main/assets/seed_goals.json`) enthalten. Button „Aktualisieren“ oben rechts. Beim ersten Start ohne
Daten wird automatisch geladen (Internet nötig); danach funktioniert die App mit dem gespeicherten Stand auch offline.

## APK bauen

**Variante A – Android Studio (Windows/Mac/Linux)**
1. Android Studio installieren, Ordner dieses Projekts öffnen („Open“), warten bis der Gradle-Sync fertig ist.
2. Menü *Build → Build Bundle(s) / APK(s) → Build APK(s)*.
3. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

**Variante B – ohne eigene Entwicklungsumgebung (GitHub)**
1. Neues (privates) Repository auf github.com anlegen und den Inhalt dieses Ordners hochladen (inkl. `.github`).
2. Reiter *Actions → APK bauen → Run workflow*. Nach wenigen Minuten steht unter *Artifacts* die Datei `KirchheimHandball-APK`.

## Auf dem Handy installieren
1. APK aufs Handy übertragen (E-Mail, Cloud, USB).
2. Antippen; Android fragt einmalig, ob die Quelle (z. B. Dateien/Chrome) Apps installieren darf → erlauben.
3. Installieren, öffnen, „Aktualisieren“ tippen.

## Technik
Kotlin, keine Fremd-Bibliothek außer PDFBox-Android (Textauslesen der PDFs). Mindestens Android 8.0.
