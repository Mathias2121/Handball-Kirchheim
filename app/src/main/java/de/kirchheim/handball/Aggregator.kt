package de.kirchheim.handball

/** Reine Rechenlogik ohne Android-Abhängigkeiten. */
object Aggregator {

    /** Normalisiert Vereinsnamen: "SG HDKirchheim" (Bericht) und "SG HD-Kirchheim" (Tabelle) sind dasselbe Team. */
    fun teamKey(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9äöüß]"), "")

    /**
     * Torschützenliste über alle ausgewerteten Spiele.
     * @param goals Spielnummer -> Torverteilung
     */
    fun scorers(games: List<Game>, goals: Map<String, ReportGoals>): List<Scorer> {
        data class Acc(var name: String, var team: String, var g: Int = 0, var s: Int = 0, var n: Int = 0)
        val acc = LinkedHashMap<String, Acc>()
        for (game in games) {
            val rg = goals[game.id] ?: continue
            fun add(map: Map<String, PlayerGoals>, team: String) {
                for ((name, pg) in map) {
                    val a = acc.getOrPut(teamKey(team) + "|" + name) { Acc(name, team) }
                    a.g += pg.goals
                    a.s += pg.sevenMeter
                    a.n++
                }
            }
            add(rg.home, game.home)
            add(rg.guest, game.guest)
        }
        val sorted = acc.values.sortedWith(
            compareByDescending<Acc> { it.g }.thenBy { it.n }.thenBy { it.name }
        )
        var rank = 0
        var prevGoals = -1
        return sorted.mapIndexed { i, a ->
            if (a.g != prevGoals) { rank = i + 1; prevGoals = a.g }
            Scorer(a.name, a.team, a.g, a.s, a.n, rank)
        }
    }

    /** Prüft einen Bericht gegen den offiziellen Spielstand; liefert eine Warnung oder null. */
    fun validate(game: Game, rg: ReportGoals): String? {
        val h = game.homeGoals ?: return null
        val g = game.guestGoals ?: return null
        // Am grünen Tisch gewertete Spiele: Verbandsergebnis (2:0) passt nie zum Spielverlauf.
        if (game.wertung != Wertung.KEINE) {
            return if (rg.consistent) null else "Spiel ${game.id}: Torsumme passt nicht zum Spielverlauf"
        }
        return if (rg.finalHome != h || rg.finalGuest != g || !rg.consistent)
            "Spiel ${game.id}: Bericht ${rg.finalHome}:${rg.finalGuest} passt nicht zum Ergebnis $h:$g"
        else null
    }

    /** Anteil nicht zugeordneter Tore, als Hinweis in der Torschützenliste. */
    fun unassigned(goals: Map<String, ReportGoals>): Int =
        goals.values.sumOf { it.unassignedHome + it.unassignedGuest }
}
