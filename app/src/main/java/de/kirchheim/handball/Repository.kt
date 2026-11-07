package de.kirchheim.handball

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Holt Tabelle/Spielplan vom Handball4All-Dienst (derselbe, den die BWHV-Seite nutzt) und
 * wertet neue Spielberichte (PDF) direkt auf dem Handy aus.
 */
class Repository(private val context: Context) {
    private val leagueFile = File(context.filesDir, "league.json")
    private val goalsFile = File(context.filesDir, "goals_cache.json")

    private fun seedGoals(): Map<String, ReportGoals> = try {
        GoalsJson.decode(context.assets.open("seed_goals.json").bufferedReader().readText())
    } catch (e: Exception) { emptyMap() }

    private fun cachedGoals(): Map<String, ReportGoals> = try {
        if (goalsFile.exists()) GoalsJson.decode(goalsFile.readText()) else emptyMap()
    } catch (e: Exception) { emptyMap() }

    /** Lädt den zuletzt gespeicherten Stand ohne Netzwerk. */
    fun loadCached(): AppState {
        val league = try {
            if (leagueFile.exists()) ApiParser.parseLeague(leagueFile.readText()) else null
        } catch (e: Exception) { null }
        return AppState(league, seedGoals() + cachedGoals(), emptyList(), if (leagueFile.exists()) leagueFile.lastModified() else 0L)
    }

    /** Netzwerk-Aktualisierung. Wirft bei Fehlern der Liga-Abfrage; PDF-Fehler werden als Warnungen gesammelt. */
    fun refresh(onProgress: (String) -> Unit): AppState {
        onProgress("Lade Tabelle und Spielplan …")
        val text = httpGet(ApiParser.LEAGUE_URL).toString(Charsets.UTF_8)
        val league = ApiParser.parseLeague(text)
        leagueFile.writeText(text)

        val goals = LinkedHashMap<String, ReportGoals>()
        goals.putAll(seedGoals())
        goals.putAll(cachedGoals())
        val warnings = ArrayList<String>()

        val todo = league.games.filter { it.played && it.sgid.isNotEmpty() && !goals.containsKey(it.id) }
        var n = 0
        for (game in todo) {
            n++
            onProgress("Werte Spielbericht $n von ${todo.size} aus …")
            try {
                val bytes = httpGet(league.reportUrlBase + game.sgid)
                val lines = PdfReader.textLines(context, ByteArrayInputStream(bytes))
                val rg = ReportParser.parse(lines)
                if (rg == null) {
                    warnings.add("Spiel ${game.id}: im Bericht keine Tore gefunden")
                    continue
                }
                val problem = Aggregator.validate(game, rg)
                if (problem != null) warnings.add(problem) else goals[game.id] = rg
            } catch (e: Exception) {
                warnings.add("Spiel ${game.id}: Bericht nicht lesbar (${e.javaClass.simpleName})")
            }
        }
        goalsFile.writeText(GoalsJson.encode(goals))
        return AppState(league, goals, warnings, System.currentTimeMillis())
    }

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "KirchheimHandball/1.0 (Android)")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
