package de.kirchheim.handball

/**
 * Wertet den Text eines Handball4All-Spielberichts (Presseinformation) aus.
 * Im Abschnitt "Spielverlauf" steht jedes Tor als Zeile wie
 * "20:03:58 02:47 1:0 Tor durch Mathias Backes (13, SG HDKirchheim)".
 * Fehlt der Name, ist das Tor keinem Spieler zugeordnet. Die Seite steht im Spielstand:
 * stieg der Heimwert, hat Heim getroffen, sonst Gast.
 */
object ReportParser {
    private val goalLine = Regex("""(\d+):(\d+)\s+(7m-)?Tor durch\s*(.*)$""")
    private val playerPart = Regex("""^(.*?)\s*\((\d+),\s*[^)]*\)\s*$""")

    /** Gibt null zurück, wenn der Text kein einziges Tor enthält. */
    fun parse(lines: List<String>): ReportGoals? {
        val home = LinkedHashMap<String, IntArray>()
        val guest = LinkedHashMap<String, IntArray>()
        var unassignedHome = 0
        var unassignedGuest = 0
        var prevHome = 0
        var prevGuest = 0
        var seen = false

        for (raw in lines) {
            val line = raw.replace(Regex("\\s+"), " ").trim()
            val m = goalLine.find(line) ?: continue
            val h = m.groupValues[1].toInt()
            val g = m.groupValues[2].toInt()
            val homeScored = when {
                h > prevHome -> true
                g > prevGuest -> false
                else -> continue
            }
            prevHome = h
            prevGuest = g
            seen = true

            val seven = m.groupValues[3].isNotEmpty()
            val who = playerPart.find(m.groupValues[4].trim())
            val name = who?.groupValues?.get(1)?.trim().orEmpty()
            if (name.isEmpty()) {
                if (homeScored) unassignedHome++ else unassignedGuest++
                continue
            }
            val bag = if (homeScored) home else guest
            val entry = bag.getOrPut(name) { IntArray(2) }
            entry[0]++
            if (seven) entry[1]++
        }
        if (!seen) return null
        return ReportGoals(
            home = home.mapValues { PlayerGoals(it.value[0], it.value[1]) },
            guest = guest.mapValues { PlayerGoals(it.value[0], it.value[1]) },
            unassignedHome = unassignedHome,
            unassignedGuest = unassignedGuest,
            finalHome = prevHome,
            finalGuest = prevGuest
        )
    }
}
