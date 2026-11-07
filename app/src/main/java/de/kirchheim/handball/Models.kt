package de.kirchheim.handball

import java.time.LocalDate

/** Eine Zeile der Tabelle, wie sie der Verband liefert. */
data class TableRow(
    val rank: Int,
    val team: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val pointsFor: Int,
    val pointsAgainst: Int
) {
    val goals: String get() = "$goalsFor:$goalsAgainst"
    val points: String get() = "$pointsFor:$pointsAgainst"
    val isKirchheim: Boolean get() = team.contains("Kirchheim")
}

/** Spiel ohne Ergebnis, das am grünen Tisch mit 2:0 gewertet wurde. */
enum class Wertung { KEINE, HEIM, GAST }

data class Game(
    val id: String,
    val sgid: String,
    val date: LocalDate,
    val time: String,
    val hall: String,
    val hallName: String,
    val home: String,
    val guest: String,
    val homeGoals: Int?,
    val guestGoals: Int?,
    val wertung: Wertung,
    val note: String
) {
    val played: Boolean get() = homeGoals != null && guestGoals != null

    /** Angezeigter Stand: bei gewerteten Spielen 2:0 beziehungsweise 0:2. */
    val shownHome: Int?
        get() = when (wertung) {
            Wertung.HEIM -> 2
            Wertung.GAST -> 0
            Wertung.KEINE -> homeGoals
        }
    val shownGuest: Int?
        get() = when (wertung) {
            Wertung.HEIM -> 0
            Wertung.GAST -> 2
            Wertung.KEINE -> guestGoals
        }

    val homeIsKirchheim: Boolean get() = home.contains("Kirchheim")
    val guestIsKirchheim: Boolean get() = guest.contains("Kirchheim")
    val involvesKirchheim: Boolean get() = homeIsKirchheim || guestIsKirchheim

    /** 'S', 'U' oder 'N' aus Sicht von Kirchheim, null wenn offen oder Kirchheim nicht beteiligt. */
    fun outcomeForKirchheim(): Char? {
        val h = shownHome ?: return null
        val g = shownGuest ?: return null
        if (!involvesKirchheim) return null
        if (h == g) return 'U'
        val kirchheimWon = if (homeIsKirchheim) h > g else g > h
        return if (kirchheimWon) 'S' else 'N'
    }
}

data class League(
    val name: String,
    val headline1: String,
    val headline2: String,
    val actualized: String,
    val reportUrlBase: String,
    val table: List<TableRow>,
    val games: List<Game>
)

/** Tore eines Spielers in einem Spiel, davon Siebenmeter. */
data class PlayerGoals(val goals: Int, val sevenMeter: Int)

/** Ausgewertete Torverteilung eines Spielberichts. */
data class ReportGoals(
    val home: Map<String, PlayerGoals>,
    val guest: Map<String, PlayerGoals>,
    val unassignedHome: Int,
    val unassignedGuest: Int,
    val finalHome: Int,
    val finalGuest: Int
) {
    val sumHome: Int get() = home.values.sumOf { it.goals } + unassignedHome
    val sumGuest: Int get() = guest.values.sumOf { it.goals } + unassignedGuest

    /** Stimmt die Summe der Einzeltore mit dem Endstand des Berichts überein? */
    val consistent: Boolean get() = sumHome == finalHome && sumGuest == finalGuest
}

data class Scorer(
    val name: String,
    val team: String,
    val goals: Int,
    val sevenMeter: Int,
    val games: Int,
    val rank: Int
)

data class AppState(
    val league: League?,
    val goals: Map<String, ReportGoals>,
    val warnings: List<String>,
    val updatedAtMillis: Long
)
