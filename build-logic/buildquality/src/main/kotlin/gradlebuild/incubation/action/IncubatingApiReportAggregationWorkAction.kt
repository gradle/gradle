/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.incubation.action

import org.gradle.api.logging.Logger
import org.gradle.workers.WorkAction
import org.slf4j.LoggerFactory


abstract class IncubatingApiReportAggregationWorkAction : WorkAction<IncubatingApiReportAggregationParameter> {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(IncubatingApiReportAggregationWorkAction::class.java.name) as Logger
        const val GITHUB_BASE_URL = "https://github.com/gradle/gradle/blob"

        /**
         * APIs that have been incubating for strictly more than this many minor releases are
         * flagged as "long-incubating" and highlighted in the report. Single source of truth,
         * surfaced both in the table highlighting and in the page footer.
         */
        const val LONG_INCUBATING_THRESHOLD = 4

        private const val VERSION_NOT_FOUND = "Not found"
    }

    override fun execute() {
        val timeline = buildVersionTimeline()
        val currentCommit = parameters.currentCommit.get()

        val entries = mutableListOf<Entry>()
        parameters.reports.files.sorted().forEach { file ->
            val project = file.nameWithoutExtension
            file.forEachLine(Charsets.UTF_8) { line ->
                if (line.isBlank()) return@forEachLine
                val record = parseLine(line)
                val sinceKnown = record.version != VERSION_NOT_FOUND
                val minor = if (sinceKnown) ReleasedVersions.toMinor(record.version) else null
                entries.add(
                    Entry(
                        name = record.name,
                        kind = IncubatingApiKind.fromString(record.kind),
                        since = if (sinceKnown) record.version else "",
                        sinceKnown = sinceKnown,
                        sinceDate = minor?.let { timeline.dateOf(it) },
                        project = project,
                        age = minor?.let { timeline.ageOf(it) },
                        url = githubUrl(record.relativePath, record.lineNumber, currentCommit),
                        category = toCategory(record.version, project)
                    )
                )
            }
        }

        generateHtmlReport(entries, timeline)
        LOGGER.lifecycle("Generated incubating html report to file://${parameters.htmlReportFile.get().asFile.absolutePath}")

        generateCsvReport(entries, currentCommit)
        LOGGER.lifecycle("Generated incubating csv report to file://${parameters.csvReportFile.get().asFile.absolutePath}")
    }

    /**
     * Parses a `version;releaseDate;kind;name;relativePath;lineNumber` record. The `name` field
     * may itself contain semicolons, so the surrounding fixed fields are peeled off from both ends.
     */
    private
    fun parseLine(line: String): Record {
        val parts = line.split(';')
        require(parts.size >= 5) {
            "Malformed incubating report record (expected 'version;releaseDate;kind;name;relativePath;lineNumber' but got ${parts.size} fields): $line"
        }
        val version = parts[0]
        val kind = parts[2]
        val lineNumber = parts.last().toIntOrNull() ?: -1
        val relativePath = parts[parts.size - 2]
        val name = parts.subList(3, parts.size - 2).joinToString(";")
        return Record(version, kind, name, relativePath, lineNumber)
    }

    private
    fun buildVersionTimeline(): VersionTimeline {
        val released = ReleasedVersions.parse(parameters.releasedVersionsFile.get().asFile)
        // released is newest-first; build an oldest-first, distinct list of minors keeping the earliest date.
        val orderedMinorDates = LinkedHashMap<String, String>()
        released.asReversed().forEach { rv ->
            orderedMinorDates.putIfAbsent(ReleasedVersions.toMinor(rv.version), rv.date)
        }
        val currentVersion = parameters.versionFile.get().asFile.readText().trim()
        // The version under development is the newest point on the timeline, even though it is not released yet.
        orderedMinorDates.putIfAbsent(ReleasedVersions.toMinor(currentVersion), "unreleased")
        return VersionTimeline(currentVersion, orderedMinorDates)
    }

    private
    fun githubUrl(relativePath: String, lineNumber: Int, currentCommit: String) =
        "$GITHUB_BASE_URL/$currentCommit/$relativePath#L$lineNumber".urlEncodeSpace()

    private
    fun toCategory(version: String, gradleModule: String) = when {
        gradleModule.endsWith("-native") || gradleModule in listOf("model-core", "platform-base", "testing-base") -> "Software Model and Native"
        else -> "Incubating since $version"
    }

    private
    fun generateHtmlReport(entries: List<Entry>, timeline: VersionTimeline) {
        val outputFile = parameters.htmlReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        val currentCommit = parameters.currentCommit.get()
        val commitUrl = "https://github.com/gradle/gradle/commit/$currentCommit"
        val csvFileName = parameters.csvReportFile.get().asFile.name
        val rowsJson = entries
            .sortedBy { it.name.lowercase() }
            .joinToString(separator = ",\n", prefix = "[", postfix = "]") { it.toJson() }
        val versionOrderJson = timeline.orderedMinors.joinToString(separator = ",", prefix = "[", postfix = "]") {
            "\"${it.jsonEscape()}\""
        }

        outputFile.writeText(htmlPage(currentCommit, commitUrl, csvFileName, entries.size, rowsJson, versionOrderJson), Charsets.UTF_8)
    }

    private
    fun generateCsvReport(entries: List<Entry>, currentCommit: String) {
        val outputFile = parameters.csvReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println("Link;Platform/Subproject;Incubating since")
            entries
                .sortedWith(compareBy({ it.category }, { it.project }, { it.name }))
                .forEach { entry ->
                    val since = entry.category.removePrefix("Incubating since ")
                    writer.println("=HYPERLINK(\"${entry.url}\",\"${entry.name}\");${entry.project};$since;")
                }
        }
    }

    private
    fun Entry.toJson(): String = buildString {
        append("{")
        append("\"name\":\"${name.jsonEscape()}\",")
        append("\"kind\":\"${kind.name.lowercase()}\",")
        append("\"since\":\"${since.jsonEscape()}\",")
        append("\"sinceKnown\":$sinceKnown,")
        append("\"sinceDate\":${sinceDate?.let { "\"${it.jsonEscape()}\"" } ?: "null"},")
        append("\"project\":\"${project.jsonEscape()}\",")
        append("\"age\":${age ?: "null"},")
        append("\"url\":\"${url.jsonEscape()}\"")
        append("}")
    }

    private
    fun htmlPage(
        currentCommit: String,
        commitUrl: String,
        csvFileName: String,
        total: Int,
        rowsJson: String,
        versionOrderJson: String
    ): String {
        val shortCommit = currentCommit.take(10)
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Incubating APIs</title>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
    <meta content="width=device-width, initial-scale=1" name="viewport">
    <link type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">
    <style>
        :root {
            --warn-bg: #fff6e5;
            --warn-border: #e0a800;
            --unknown-bg: #f1f1f4;
            --accent: #1ba0c4;
            --muted: #6b6f76;
            --border: #d7d9dd;
        }
        body { font-family: Lato, sans-serif; margin: 0 auto; max-width: 1200px; padding: 1rem 1.5rem 4rem; color: #1c2126; }
        h1 { margin-bottom: .25rem; }
        .subtitle { color: var(--muted); margin: 0 0 1.5rem; font-size: .95rem; }
        .subtitle a { color: var(--accent); }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
        .card { border: 1px solid var(--border); border-radius: 8px; padding: .9rem 1rem; background: #fff; }
        .card h3 { margin: 0 0 .6rem; font-size: .85rem; text-transform: uppercase; letter-spacing: .05em; color: var(--muted); }
        .big-number { font-size: 2rem; font-weight: 700; line-height: 1; }
        .big-number .label { font-size: .9rem; font-weight: 400; color: var(--muted); margin-left: .4rem; }
        .dist-row, .top-row { display: grid; grid-template-columns: 5.5rem 1fr 2.5rem; align-items: center; gap: .5rem; font-size: .82rem; margin: .15rem 0; }
        .top-row { grid-template-columns: 1fr 2.5rem; }
        .bar { background: var(--accent); height: 11px; border-radius: 3px; min-width: 2px; }
        .bar-track { background: #eef0f3; border-radius: 3px; }
        .count { text-align: right; color: var(--muted); font-variant-numeric: tabular-nums; }
        .dist-row { cursor: pointer; border-radius: 4px; }
        .dist-row:hover { background: #eef6f9; }
        .dist-row.active { background: #d9f0f6; }
        .dist-row.active span { font-weight: 700; color: #14708a; }
        .card h3 .hint { text-transform: none; letter-spacing: 0; font-weight: 400; color: var(--muted); }
        .actions { display: flex; flex-wrap: wrap; gap: .6rem; align-items: center; margin-bottom: 1rem; }
        .actions input[type=search] { flex: 1 1 220px; min-width: 180px; padding: .45rem .6rem; border: 1px solid var(--border); border-radius: 6px; font-size: .9rem; }
        .actions select { padding: .42rem .5rem; border: 1px solid var(--border); border-radius: 6px; font-size: .85rem; background: #fff; }
        .btn { padding: .45rem .8rem; border: 1px solid var(--accent); border-radius: 6px; background: var(--accent); color: #fff; text-decoration: none; font-size: .85rem; cursor: pointer; }
        .btn.secondary { background: #fff; color: var(--accent); }
        :focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
        table { border-collapse: collapse; width: 100%; font-size: .88rem; }
        thead th { text-align: left; border-bottom: 2px solid var(--border); padding: .55rem .6rem; cursor: pointer; user-select: none; white-space: nowrap; position: sticky; top: 0; background: #fff; }
        thead th .arrow { color: var(--muted); font-size: .75rem; }
        tbody td { border-bottom: 1px solid #edeef1; padding: .45rem .6rem; vertical-align: top; }
        tbody tr.row-long { background: var(--warn-bg); }
        tbody tr.row-unknown { background: var(--unknown-bg); }
        td.api a { color: var(--accent); text-decoration: none; word-break: break-word; }
        td.api a:hover { text-decoration: underline; }
        .kind-pill { display: inline-block; font-size: .72rem; padding: .05rem .45rem; border-radius: 10px; background: #eef0f3; color: #3a3f45; text-transform: lowercase; }
        .badge-warn { color: var(--warn-border); font-weight: 700; }
        .badge-unknown { color: var(--muted); font-style: italic; }
        .nowrap { white-space: nowrap; }
        .empty { padding: 2rem; text-align: center; color: var(--muted); }
        footer { margin-top: 2rem; padding-top: 1rem; border-top: 1px solid var(--border); color: var(--muted); font-size: .82rem; }
        footer a { color: var(--accent); }
    </style>
</head>
<body>
    <h1>Incubating APIs</h1>
    <p class="subtitle">
        Gradle <strong>${timelineVersion()}</strong> &middot;
        <strong>$total</strong> incubating declarations &middot;
        source commit <a href="$commitUrl" target="_blank" rel="noopener"><code>$shortCommit</code></a>
    </p>

    <section class="summary" id="summary" aria-label="Summary"></section>

    <div class="actions">
        <input type="search" id="search" placeholder="Search API name&hellip;" aria-label="Search API name">
        <label>since <select id="filter-since" aria-label="Filter by since version"></select></label>
        <label>project <select id="filter-project" aria-label="Filter by project"></select></label>
        <label>kind <select id="filter-kind" aria-label="Filter by kind"></select></label>
        <label>age <select id="filter-age" aria-label="Filter by age">
            <option value="">any</option>
            <option value="fresh">within threshold</option>
            <option value="long">long-incubating</option>
            <option value="unknown">unknown since</option>
        </select></label>
        <a class="btn" id="download-csv" href="$csvFileName" download>Download CSV</a>
        <a class="btn secondary" href="#" id="clear-filters">Clear filters</a>
    </div>

    <table id="report">
        <thead>
            <tr>
                <th scope="col" data-key="name" aria-sort="ascending">API <span class="arrow"></span></th>
                <th scope="col" data-key="kind">Kind <span class="arrow"></span></th>
                <th scope="col" data-key="since">Since <span class="arrow"></span></th>
                <th scope="col" data-key="project">Project <span class="arrow"></span></th>
                <th scope="col" data-key="age">Age <span class="arrow"></span></th>
            </tr>
        </thead>
        <tbody id="rows"></tbody>
    </table>
    <p class="empty" id="empty" hidden>No incubating APIs match the current filters.</p>

    <footer>
        <p>
            Rows highlighted in amber have been incubating for more than <strong>$LONG_INCUBATING_THRESHOLD</strong>
            minor releases. Rows in grey have no resolvable <code>@since</code> tag ("unknown since").
        </p>
        <p>The data behind this page is available as <a href="$csvFileName">$csvFileName</a> and inline as
            <a href="#" id="view-json">JSON</a> (logged to the browser console for scraping).</p>
    </footer>

    <script id="data" type="application/json">$rowsJson</script>
    <script>
        var THRESHOLD = $LONG_INCUBATING_THRESHOLD;
        var VERSION_ORDER = $versionOrderJson;
        var DATA = JSON.parse(document.getElementById('data').textContent);

        // The @since tags are inconsistent (some "9.3.0", some "8.5"); normalise to major.minor so
        // they line up with VERSION_ORDER for chronological sorting.
        function minorOf(v) {
            var parts = String(v).split('.');
            if (parts.length >= 2) {
                var digits = parts[1].match(/^[0-9]+/);
                return parts[0] + '.' + (digits ? digits[0] : parts[1]);
            }
            return v;
        }
        function versionRank(v) {
            var idx = VERSION_ORDER.indexOf(minorOf(v));
            return idx < 0 ? -1 : idx;
        }

        function el(tag, attrs, text) {
            var node = document.createElement(tag);
            if (attrs) { for (var k in attrs) { if (attrs[k] != null) node.setAttribute(k, attrs[k]); } }
            if (text != null) node.textContent = text;
            return node;
        }

        function ageBucket(row) {
            if (!row.sinceKnown) return 'unknown';
            if (row.age == null) return 'unknown';
            return row.age > THRESHOLD ? 'long' : 'fresh';
        }

        function uniqueSorted(values, orderFn) {
            var seen = {}, out = [];
            values.forEach(function (v) { if (!seen[v]) { seen[v] = true; out.push(v); } });
            out.sort(orderFn);
            return out;
        }

        function fillSelect(select, options) {
            select.appendChild(el('option', { value: '' }, 'any'));
            options.forEach(function (o) { select.appendChild(el('option', { value: o }, o)); });
        }

        var search = document.getElementById('search');
        var fSince = document.getElementById('filter-since');
        var fProject = document.getElementById('filter-project');
        var fKind = document.getElementById('filter-kind');
        var fAge = document.getElementById('filter-age');
        var tbody = document.getElementById('rows');
        var empty = document.getElementById('empty');

        fillSelect(fSince, uniqueSorted(DATA.filter(function (r) { return r.sinceKnown; }).map(function (r) { return r.since; }),
            function (a, b) { return versionRank(a) - versionRank(b); }));
        fillSelect(fProject, uniqueSorted(DATA.map(function (r) { return r.project; }), undefined));
        fillSelect(fKind, uniqueSorted(DATA.map(function (r) { return r.kind; }), undefined));

        var sortKey = 'name', sortDir = 1;

        function compare(a, b) {
            var primary;
            if (sortKey === 'age') {
                primary = (a.age == null ? Infinity : a.age) - (b.age == null ? Infinity : b.age);
            } else if (sortKey === 'since') {
                primary = versionRank(a.since) - versionRank(b.since);
            } else {
                primary = String(a[sortKey]).toLowerCase().localeCompare(String(b[sortKey]).toLowerCase());
            }
            if (primary !== 0) return primary * sortDir;
            // Stable multi-key: ties fall back to alphabetical API name.
            return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
        }

        function currentFilter() {
            var q = search.value.trim().toLowerCase();
            return function (r) {
                if (q && r.name.toLowerCase().indexOf(q) === -1) return false;
                if (fSince.value && r.since !== fSince.value) return false;
                if (fProject.value && r.project !== fProject.value) return false;
                if (fKind.value && r.kind !== fKind.value) return false;
                if (fAge.value && ageBucket(r) !== fAge.value) return false;
                return true;
            };
        }

        function renderSummary() {
            var summary = document.getElementById('summary');
            summary.innerHTML = '';
            var total = DATA.length;
            var longCount = DATA.filter(function (r) { return ageBucket(r) === 'long'; }).length;
            var unknownCount = DATA.filter(function (r) { return !r.sinceKnown; }).length;

            var totals = el('div', { class: 'card' });
            totals.appendChild(el('h3', null, 'Totals'));
            var n = el('div', { class: 'big-number' }, String(total));
            n.appendChild(el('span', { class: 'label' }, 'incubating APIs'));
            totals.appendChild(n);
            totals.appendChild(el('div', { class: 'top-row' }))
                .appendChild(el('span', null, longCount + ' long-incubating (> ' + THRESHOLD + ' releases)'));
            totals.appendChild(el('div', { class: 'top-row' }))
                .appendChild(el('span', null, unknownCount + ' with unknown since'));
            summary.appendChild(totals);

            // Per-since distribution
            var bySince = {};
            DATA.forEach(function (r) { var k = r.sinceKnown ? r.since : 'unknown'; bySince[k] = (bySince[k] || 0) + 1; });
            var sinceKeys = Object.keys(bySince).sort(function (a, b) {
                if (a === 'unknown') return 1;
                if (b === 'unknown') return -1;
                return versionRank(a) - versionRank(b);
            });
            var maxSince = Math.max.apply(null, sinceKeys.map(function (k) { return bySince[k]; }));
            var distCard = el('div', { class: 'card' });
            var distHeader = el('h3', null, 'By since version ');
            distHeader.appendChild(el('span', { class: 'hint' }, '(click to filter)'));
            distCard.appendChild(distHeader);
            sinceKeys.forEach(function (k) {
                var active = (k === 'unknown') ? (fAge.value === 'unknown') : (fSince.value === k);
                var row = el('div', { class: 'dist-row' + (active ? ' active' : ''), role: 'button', tabindex: '0' });
                row.setAttribute('aria-label', 'Filter table to APIs incubating since ' + k);
                row.appendChild(el('span', null, k));
                var track = el('div', { class: 'bar-track' });
                var bar = el('div', { class: 'bar' });
                bar.style.width = Math.max(2, Math.round(bySince[k] / maxSince * 100)) + '%';
                track.appendChild(bar);
                row.appendChild(track);
                row.appendChild(el('span', { class: 'count' }, String(bySince[k])));
                row.addEventListener('click', function () { applyDistFilter(k); });
                row.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); applyDistFilter(k); }
                });
                distCard.appendChild(row);
            });
            summary.appendChild(distCard);

            // Top projects
            var byProject = {};
            DATA.forEach(function (r) { byProject[r.project] = (byProject[r.project] || 0) + 1; });
            var topProjects = Object.keys(byProject).sort(function (a, b) { return byProject[b] - byProject[a]; }).slice(0, 8);
            var projCard = el('div', { class: 'card' });
            projCard.appendChild(el('h3', null, 'Top projects'));
            topProjects.forEach(function (p) {
                var row = el('div', { class: 'top-row' });
                row.appendChild(el('span', null, p));
                row.appendChild(el('span', { class: 'count' }, String(byProject[p])));
                projCard.appendChild(row);
            });
            summary.appendChild(projCard);
        }

        function renderRows() {
            var rows = DATA.filter(currentFilter()).slice().sort(compare);
            tbody.innerHTML = '';
            empty.hidden = rows.length !== 0;
            rows.forEach(function (r) {
                var tr = el('tr');
                var bucket = ageBucket(r);
                if (bucket === 'long') tr.className = 'row-long';
                else if (!r.sinceKnown) tr.className = 'row-unknown';

                var apiTd = el('td', { class: 'api' });
                apiTd.appendChild(el('a', { href: r.url, target: '_blank', rel: 'noopener' }, r.name));
                tr.appendChild(apiTd);

                var kindTd = el('td');
                kindTd.appendChild(el('span', { class: 'kind-pill' }, r.kind));
                tr.appendChild(kindTd);

                var sinceTd = el('td', { class: 'nowrap' });
                if (r.sinceKnown) {
                    sinceTd.textContent = r.since;
                    sinceTd.title = r.sinceDate ? ('Released ' + r.sinceDate) : 'Unreleased';
                } else {
                    sinceTd.appendChild(el('span', { class: 'badge-unknown' }, 'unknown'));
                }
                tr.appendChild(sinceTd);

                tr.appendChild(el('td', null, r.project));

                var ageTd = el('td', { class: 'nowrap' });
                if (!r.sinceKnown || r.age == null) {
                    ageTd.appendChild(el('span', { class: 'badge-unknown' }, r.sinceKnown ? '—' : 'unknown'));
                } else if (r.age > THRESHOLD) {
                    ageTd.appendChild(el('span', { class: 'badge-warn' }, r.age + ' releases ⚠'));
                } else {
                    ageTd.textContent = r.age + ' releases';
                }
                tr.appendChild(ageTd);

                tbody.appendChild(tr);
            });
        }

        function updateAriaSort() {
            var headers = document.querySelectorAll('thead th');
            headers.forEach(function (th) {
                var arrow = th.querySelector('.arrow');
                if (th.getAttribute('data-key') === sortKey) {
                    th.setAttribute('aria-sort', sortDir === 1 ? 'ascending' : 'descending');
                    arrow.textContent = sortDir === 1 ? '▲' : '▼';
                } else {
                    th.removeAttribute('aria-sort');
                    arrow.textContent = '';
                }
            });
        }

        document.querySelectorAll('thead th').forEach(function (th) {
            th.addEventListener('click', function () {
                var key = th.getAttribute('data-key');
                if (key === sortKey) { sortDir = -sortDir; } else { sortKey = key; sortDir = 1; }
                updateAriaSort();
                renderRows();
            });
        });

        // Re-render the summary too so the "By since version" active highlight tracks the filters.
        function update() {
            renderSummary();
            renderRows();
        }

        // Clicking a distribution row sets up the matching filter (toggling it off if already active).
        function applyDistFilter(k) {
            var selected;
            if (k === 'unknown') {
                selected = fAge.value !== 'unknown';
                fAge.value = selected ? 'unknown' : '';
                fSince.value = '';
            } else {
                selected = fSince.value !== k;
                fSince.value = selected ? k : '';
                if (fAge.value === 'unknown') fAge.value = '';
            }
            update();
            // Only jump to the table when turning a filter on, not when clearing it.
            if (selected) {
                var report = document.getElementById('report');
                if (report.scrollIntoView) report.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }

        [search, fSince, fProject, fKind, fAge].forEach(function (c) {
            c.addEventListener('input', update);
        });

        document.getElementById('clear-filters').addEventListener('click', function (e) {
            e.preventDefault();
            search.value = ''; fSince.value = ''; fProject.value = ''; fKind.value = ''; fAge.value = '';
            update();
        });

        document.getElementById('view-json').addEventListener('click', function (e) {
            e.preventDefault();
            console.log(JSON.stringify(DATA, null, 2));
            window.alert('The full JSON dataset has been logged to the browser console.');
        });

        renderSummary();
        updateAriaSort();
        renderRows();
    </script>
</body>
</html>
"""
    }

    private
    fun timelineVersion(): String = parameters.versionFile.get().asFile.readText().trim()

    private
    fun String.urlEncodeSpace() = replace(" ", "%20")

    private
    fun String.jsonEscape(): String = buildString {
        for (c in this@jsonEscape) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}


private
data class Record(
    val version: String,
    val kind: String,
    val name: String,
    val relativePath: String,
    val lineNumber: Int
)


private
data class Entry(
    val name: String,
    val kind: IncubatingApiKind,
    val since: String,
    val sinceKnown: Boolean,
    val sinceDate: String?,
    val project: String,
    val age: Int?,
    val url: String,
    val category: String
)


private
class VersionTimeline(
    val currentVersion: String,
    private val orderedMinorDates: LinkedHashMap<String, String>
) {
    /** Distinct minor versions, oldest-first, including the version currently under development. */
    val orderedMinors: List<String> = orderedMinorDates.keys.toList()

    /** Number of minor releases between [minor] and the newest point on the timeline, or null if unknown. */
    fun ageOf(minor: String): Int? {
        val idx = orderedMinors.indexOf(minor)
        return if (idx < 0) null else orderedMinors.size - 1 - idx
    }

    fun dateOf(minor: String): String? = orderedMinorDates[minor]?.takeIf { it != "unreleased" }
}
