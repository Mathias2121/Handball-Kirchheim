package de.kirchheim.handball

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Liest die JSON-Antwort des Handball4All-Dienstes, den auch die BWHV-Seite benutzt. */
object ApiParser {
    const val LEAGUE_URL = "https://spo.handball4all.de/service/if_g_json.php?ca=1&cl=164126&cmd=ps&og=216"
    const val DEFAULT_REPORT_URL = "https://spo.handball4all.de/misc/sboPublicReports.php?sGID="

    fun parseLeague(text: String): League {
        val root = JSONArray(text).getJSONObject(0)
        val head = root.optJSONObject("head") ?: JSONObject()
        val content = root.optJSONObject("content")
            ?: throw IllegalStateException("Die Antwort des Verbands enthält keine Liga-Daten.")

        val scoreArray = content.optJSONArray("score") ?: JSONArray()
        val table = ArrayList<TableRow>()
        for (i in 0 until scoreArray.length()) {
            val o = scoreArray.getJSONObject(i)
            table.add(
                TableRow(
                    rank = o.optInt("tabScore", 0),
                    team = clean(o.str("tabTeamname")),
                    played = o.optInt("numPlayedGames", 0),
                    won = o.optInt("numWonGames", 0),
                    draw = o.optInt("numEqualGames", 0),
                    lost = o.optInt("numLostGames", 0),
                    goalsFor = o.optInt("numGoalsShot", 0),
                    goalsAgainst = o.optInt("numGoalsGot", 0),
                    pointsFor = o.optInt("pointsPlus", 0),
                    pointsAgainst = o.optInt("pointsMinus", 0)
                )
            )
        }

        val games = LinkedHashMap<String, Game>()
        collectGames(content.opt("futureGames"), games)
        collectGames(content.opt("actualGames"), games)

        val base = head.str("repURL").ifEmpty { DEFAULT_REPORT_URL }
        return League(
            name = head.str("name"),
            headline1 = head.str("headline1"),
            headline2 = head.str("headline2"),
            actualized = head.str("actualized"),
            reportUrlBase = base,
            table = table,
            games = games.values.sortedWith(compareBy({ it.date }, { it.time }, { it.id }))
        )
    }

    private fun collectGames(node: Any?, out: MutableMap<String, Game>) {
        when (node) {
            is JSONObject -> {
                val arr = node.optJSONArray("games")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val g = parseGame(o) ?: continue
                        out[g.id] = g
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectGames(node.opt(i), out)
            else -> Unit
        }
    }

    private fun parseGame(o: JSONObject): Game? {
        val id = o.str("gNo")
        if (id.isEmpty()) return null
        val date = parseDate(o.str("gDate")) ?: return null
        val hg = o.str("gHomeGoals").toIntOrNull()
        val ag = o.str("gGuestGoals").toIntOrNull()
        val played = hg != null && ag != null
        val comment = o.str("gComment")
        val wertung = when {
            !played -> Wertung.KEINE
            comment.contains("gg. Gast") -> Wertung.HEIM
            comment.contains("gg. Heim") -> Wertung.GAST
            else -> Wertung.KEINE
        }
        val note = comment
            .replace(Regex("""\(\d+:\d+\),?\s*"""), "")
            .replace(Regex("""gg\.\s*(Gast|Heim),?\s*"""), "")
            .trim()
            .trim(',')
            .trim()
        val sg = o.str("sGID")
        return Game(
            id = id,
            sgid = if (sg == "0") "" else sg,
            date = date,
            time = o.str("gTime"),
            hall = o.str("gGymnasiumNo"),
            hallName = o.str("gGymnasiumName"),
            home = clean(o.str("gHomeTeam")),
            guest = clean(o.str("gGuestTeam")),
            homeGoals = hg,
            guestGoals = ag,
            wertung = wertung,
            note = note
        )
    }

    /** "19.09.26" wird zu 19. September 2026. */
    internal fun parseDate(text: String): LocalDate? {
        val p = text.split(".")
        if (p.size < 3) return null
        val d = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().toIntOrNull() ?: return null
        var y = p[2].trim().toIntOrNull() ?: return null
        if (y < 100) y += 2000
        return try { LocalDate.of(y, m, d) } catch (e: Exception) { null }
    }

    private fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    private fun JSONObject.str(key: String): String =
        if (has(key) && !isNull(key)) get(key).toString().trim() else ""
}
