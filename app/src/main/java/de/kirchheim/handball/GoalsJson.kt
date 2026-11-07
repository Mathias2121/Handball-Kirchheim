package de.kirchheim.handball

import org.json.JSONObject

/** Speichert/liest Torverteilungen als JSON (Cache und mitgeliefertes Startdaten-Set). */
object GoalsJson {
    fun decode(text: String): Map<String, ReportGoals> {
        val root = JSONObject(text)
        val out = LinkedHashMap<String, ReportGoals>()
        for (key in root.keys()) {
            val o = root.getJSONObject(key)
            fun side(name: String): Map<String, PlayerGoals> {
                val m = LinkedHashMap<String, PlayerGoals>()
                val s = o.optJSONObject(name) ?: return m
                for (p in s.keys()) {
                    val a = s.getJSONArray(p)
                    m[p] = PlayerGoals(a.getInt(0), a.optInt(1, 0))
                }
                return m
            }
            val home = side("h")
            val guest = side("a")
            val uh = o.optInt("uh", 0)
            val ua = o.optInt("ua", 0)
            out[key] = ReportGoals(
                home, guest, uh, ua,
                home.values.sumOf { it.goals } + uh,
                guest.values.sumOf { it.goals } + ua
            )
        }
        return out
    }

    fun encode(map: Map<String, ReportGoals>): String {
        val root = JSONObject()
        for ((id, rg) in map) {
            val o = JSONObject()
            fun side(m: Map<String, PlayerGoals>): JSONObject {
                val s = JSONObject()
                for ((n, pg) in m) s.put(n, org.json.JSONArray().put(pg.goals).put(pg.sevenMeter))
                return s
            }
            o.put("h", side(rg.home)).put("a", side(rg.guest))
            o.put("uh", rg.unassignedHome).put("ua", rg.unassignedGuest)
            root.put(id, o)
        }
        return root.toString()
    }
}
