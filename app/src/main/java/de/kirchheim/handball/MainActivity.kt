package de.kirchheim.handball

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private enum class Tab(val label: String) {
        TABELLE("Tabelle"), SPIELPLAN("Spielplan"), KIRCHHEIM("Kirchheim"), TORE("Torschützen")
    }

    private lateinit var repo: Repository
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var state = AppState(null, emptyMap(), emptyList(), 0L)
    private var tab = Tab.TABELLE
    private var refreshing = false
    private var progress = ""
    private var errorText: String? = null
    private var teamFilter: String? = null
    private var gameTeamFilter: String? = null
    private var showAllScorers = false
    private val expanded = HashSet<String>()

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var subtitle: TextView
    private lateinit var refreshButton: Button
    private val tabButtons = HashMap<Tab, TextView>()

    private val dateFmt = DateTimeFormatter.ofPattern("EE dd.MM.yy", Locale.GERMAN)

    private fun c(id: Int) = getColor(id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Repository(this)
        state = repo.loadCached()
        buildLayout()
        render()
        if (state.league == null) refresh()
    }

    // ---------- Layout ----------

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(R.color.bg))
            fitsSystemWindows = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setBackgroundColor(c(R.color.primary))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("SG HD-Kirchheim", 20f, R.color.on_accent, bold = true))
        subtitle = text("", 12f, R.color.on_accent)
        titles.addView(subtitle)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        refreshButton = Button(this).apply {
            text = "Aktualisieren"
            isAllCaps = false
            setOnClickListener { refresh() }
        }
        header.addView(refreshButton)
        root.addView(header)

        scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(c(R.color.card))
        }
        for (t in Tab.values()) {
            val b = TextView(this).apply {
                text = t.label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { tab = t; render(); scroll.scrollTo(0, 0) }
            }
            tabButtons[t] = b
            bar.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bar)
        setContentView(root)
    }

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        errorText = null
        progress = "Verbinde …"
        render()
        io.execute {
            try {
                val s = repo.refresh { p -> ui.post { progress = p; render() } }
                ui.post { state = s; refreshing = false; render() }
            } catch (e: Exception) {
                ui.post {
                    refreshing = false
                    errorText = "Aktualisierung fehlgeschlagen (${e.message ?: e.javaClass.simpleName}). " +
                        "Bitte Internetverbindung prüfen. Angezeigt wird der zuletzt gespeicherte Stand."
                    render()
                }
            }
        }
    }

    // ---------- Rendering ----------

    private fun render() {
        for ((t, b) in tabButtons) {
            val sel = t == tab
            b.setTextColor(c(if (sel) R.color.accent else R.color.muted))
            b.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
        }
        refreshButton.isEnabled = !refreshing
        val league = state.league
        subtitle.text = when {
            refreshing -> progress
            league == null -> "Noch keine Daten"
            else -> "${league.headline2.ifEmpty { league.name }} · Stand ${league.actualized}"
        }
        content.removeAllViews()
        errorText?.let { content.addView(notice(it, true)) }
        if (league == null) {
            content.addView(notice(if (refreshing) "Daten werden geladen …" else "Zum Laden „Aktualisieren“ tippen (Internet nötig).", false))
            return
        }
        when (tab) {
            Tab.TABELLE -> renderTable(league)
            Tab.SPIELPLAN -> renderSchedule(league)
            Tab.KIRCHHEIM -> renderGames(league, league.games.filter { it.involvesKirchheim }, true)
            Tab.TORE -> renderScorers(league)
        }
    }

    private fun renderTable(league: League) {
        content.addView(heading(league.name.ifEmpty { "Tabelle" }))
        val head = row()
        for ((t, w) in listOf("#" to 0.5f, "Mannschaft" to 3.2f, "Sp" to 0.8f, "S" to 0.6f, "U" to 0.6f, "N" to 0.6f, "Tore" to 1.6f, "Pkt" to 1.4f)) {
            head.addView(cell(t, w, bold = true, muted = true, left = t == "Mannschaft"))
        }
        content.addView(head)
        for (r in league.table) {
            val rowView = row(highlight = r.isKirchheim)
            rowView.addView(cell(r.rank.toString(), 0.5f))
            rowView.addView(cell(r.team, 3.2f, bold = r.isKirchheim, left = true))
            rowView.addView(cell(r.played.toString(), 0.8f))
            rowView.addView(cell(r.won.toString(), 0.6f))
            rowView.addView(cell(r.draw.toString(), 0.6f))
            rowView.addView(cell(r.lost.toString(), 0.6f))
            rowView.addView(cell(r.goals, 1.6f))
            rowView.addView(cell(r.points, 1.4f, bold = true))
            content.addView(rowView)
        }
    }

    /** Gesamtspielplan mit Teamauswahl; ein Team zeigt alle seine Heim- und Auswärtsspiele. */
    private fun renderSchedule(league: League) {
        val teams = league.games.flatMap { listOf(it.home, it.guest) }
            .filter { it.isNotBlank() }
            .distinctBy { Aggregator.teamKey(it) }
            .sorted()
        if (gameTeamFilter != null && teams.none { Aggregator.teamKey(it) == gameTeamFilter }) gameTeamFilter = null
        val chips = teamChips(teams, gameTeamFilter) { gameTeamFilter = it }.apply { setPadding(0, 0, 0, dp(8)) }
        val games = league.games.filter {
            gameTeamFilter == null ||
                Aggregator.teamKey(it.home) == gameTeamFilter || Aggregator.teamKey(it.guest) == gameTeamFilter
        }
        renderGames(league, games, false, chips)
    }

    private fun renderGames(league: League, games: List<Game>, kirchheim: Boolean, chips: View? = null) {
        content.addView(heading(if (kirchheim) "Spielplan SG HD-Kirchheim" else "Gesamtspielplan"))
        chips?.let { content.addView(it) }
        if (kirchheim) {
            val sieg = games.count { it.outcomeForKirchheim() == 'S' }
            val ud = games.count { it.outcomeForKirchheim() == 'U' }
            val nd = games.count { it.outcomeForKirchheim() == 'N' }
            content.addView(notice("Bilanz: $sieg Siege · $ud Unentschieden · $nd Niederlagen · ${games.count { !it.played }} offen", false))
        }
        if (games.isEmpty()) content.addView(notice("Keine Spiele gefunden.", false))
        for (g in games) content.addView(gameCard(league, g, kirchheim))
    }

    private fun gameCard(league: League, g: Game, kirchheim: Boolean): View {
        val card = card()
        val meta = buildString {
            append(g.date.format(dateFmt)); if (g.time.isNotEmpty()) append(" · ${g.time}")
            if (g.hall.isNotEmpty() || g.hallName.isNotEmpty()) append(" · ${g.hallName.ifEmpty { "Halle ${g.hall}" }}")
        }
        card.addView(text(meta, 12f, R.color.muted))
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val teams = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        teams.addView(text(g.home, 15f, R.color.fg, bold = g.homeIsKirchheim))
        teams.addView(text(g.guest, 15f, R.color.fg, bold = g.guestIsKirchheim))
        line.addView(teams, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val score = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        score.addView(text(g.shownHome?.toString() ?: "–", 15f, R.color.fg, bold = true).apply { gravity = Gravity.END })
        score.addView(text(g.shownGuest?.toString() ?: "–", 15f, R.color.fg, bold = true).apply { gravity = Gravity.END })
        line.addView(score)
        if (kirchheim) {
            val o = g.outcomeForKirchheim()
            if (o != null) {
                val (lbl, col) = when (o) { 'S' -> "S" to R.color.win; 'U' -> "U" to R.color.draw; else -> "N" to R.color.loss }
                line.addView(text(lbl, 14f, col, bold = true).apply {
                    setPadding(dp(14), 0, dp(4), 0)
                })
            }
        }
        card.addView(line)
        if (g.wertung != Wertung.KEINE) card.addView(text("Wertung am grünen Tisch (2:0)" + if (g.note.isNotEmpty()) " · ${g.note}" else "", 12f, R.color.muted))
        else if (g.note.isNotEmpty()) card.addView(text(g.note, 12f, R.color.muted))

        val rg = state.goals[g.id]
        if (g.played && (g.sgid.isNotEmpty() || rg != null)) {
            val open = expanded.contains(g.id)
            val toggle = text(if (open) "Details ausblenden ▲" else "Torschützen & Spielbericht ▼", 13f, R.color.accent).apply {
                setPadding(0, dp(8), 0, 0)
                setOnClickListener { if (!expanded.add(g.id)) expanded.remove(g.id); render() }
            }
            card.addView(toggle)
            if (open) {
                if (rg != null) {
                    card.addView(scorerBlock(g.home, rg.home, rg.unassignedHome))
                    card.addView(scorerBlock(g.guest, rg.guest, rg.unassignedGuest))
                } else {
                    card.addView(text("Torverteilung noch nicht ausgewertet – „Aktualisieren“ tippen.", 12f, R.color.muted))
                }
                if (g.sgid.isNotEmpty()) {
                    card.addView(text("PDF-Spielbericht öffnen ↗", 13f, R.color.accent, bold = true).apply {
                        setPadding(0, dp(8), 0, 0)
                        setOnClickListener {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(league.reportUrlBase + g.sgid)))
                            } catch (e: Exception) { /* kein Browser installiert */ }
                        }
                    })
                }
            }
        }
        return card
    }

    private fun scorerBlock(team: String, goals: Map<String, PlayerGoals>, unassigned: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        box.addView(text(team, 12f, R.color.muted, bold = true))
        val parts = goals.entries.sortedByDescending { it.value.goals }.map {
            "${it.key} ${it.value.goals}" + if (it.value.sevenMeter > 0) " (${it.value.sevenMeter}×7m)" else ""
        }.toMutableList()
        if (unassigned > 0) parts.add("ohne Zuordnung $unassigned")
        box.addView(text(if (parts.isEmpty()) "keine Tore" else parts.joinToString(", "), 13f, R.color.fg))
        return box
    }

    private fun renderScorers(league: League) {
        content.addView(heading("Torschützenliste"))
        val all = Aggregator.scorers(league.games, state.goals)
        val teams = all.map { it.team }.distinctBy { Aggregator.teamKey(it) }.sorted()
        if (teamFilter != null && teams.none { Aggregator.teamKey(it) == teamFilter }) teamFilter = null

        content.addView(teamChips(teams, teamFilter) { teamFilter = it })

        val list = all.filter { teamFilter == null || Aggregator.teamKey(it.team) == teamFilter }
        val shown = if (showAllScorers) list else list.take(15)
        val head = row()
        for ((t, w) in listOf("#" to 0.6f, "Spieler" to 3.2f, "Tore" to 0.9f, "7m" to 0.8f, "Sp" to 0.8f)) head.addView(cell(t, w, bold = true, muted = true, left = t == "Spieler"))
        content.addView(head)
        for (s in shown) {
            val r = row(highlight = s.team.contains("Kirchheim"))
            r.addView(cell(s.rank.toString(), 0.6f))
            val who = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            who.addView(text(s.name, 14f, R.color.fg, bold = true))
            who.addView(text(s.team, 11f, R.color.muted))
            r.addView(who, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3.2f))
            r.addView(cell(s.goals.toString(), 0.9f, bold = true))
            r.addView(cell(s.sevenMeter.toString(), 0.8f))
            r.addView(cell(s.games.toString(), 0.8f))
            content.addView(r)
        }
        if (list.size > 15) content.addView(text(if (showAllScorers) "Nur Top 15 zeigen" else "Alle ${list.size} anzeigen", 14f, R.color.accent, bold = true).apply {
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener { showAllScorers = !showAllScorers; render() }
        })
        val played = league.games.count { it.played }
        val unassigned = Aggregator.unassigned(state.goals)
        content.addView(notice(
            "Ausgewertet: ${state.goals.size} von $played gespielten Spielen. " +
                (if (unassigned > 0) "$unassigned Tore sind im Spielbericht keinem Spieler zugeordnet. " else "") +
                "Quelle: PDF-Spielberichte, Siebenmeter-Tore zählen mit.", false))
        for (w in state.warnings) content.addView(notice(w, true))
    }

    // ---------- kleine UI-Helfer ----------

    /** Waagerechte Chip-Leiste zur Teamauswahl. [current] ist der aktive Teamschlüssel, null bedeutet „Alle“. */
    private fun teamChips(teams: List<String>, current: String?, onSelect: (String?) -> Unit): View {
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(label: String, key: String?) {
            val sel = current == key
            chips.addView(text(label, 13f, if (sel) R.color.on_accent else R.color.fg, bold = sel).apply {
                setBackgroundColor(c(if (sel) R.color.primary else R.color.card))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { onSelect(key); render() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
        }
        chip("Alle", null)
        for (t in teams) chip(t, Aggregator.teamKey(t))
        return android.widget.HorizontalScrollView(this).apply { addView(chips) }
    }

    private fun text(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(c(color))
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun heading(t: String) = text(t, 18f, R.color.fg, bold = true).apply { setPadding(0, 0, 0, dp(8)) }

    private fun notice(t: String, warn: Boolean) = text(t, 12f, if (warn) R.color.loss else R.color.muted).apply {
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(c(R.color.card))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    }

    private fun row(highlight: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        if (highlight) setBackgroundColor(c(R.color.highlight))
    }

    private fun cell(t: String, weight: Float, bold: Boolean = false, muted: Boolean = false, left: Boolean = false) =
        text(t, 13f, if (muted) R.color.muted else R.color.fg, bold).apply {
            gravity = if (left) Gravity.START else Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        }
}
