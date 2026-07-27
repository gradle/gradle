/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.removal.action

import org.gradle.api.logging.Logger
import org.gradle.workers.WorkAction
import org.slf4j.LoggerFactory


/**
 * Aggregates the per-project txt reports into a single html + csv report, mirroring the incubating-API
 * aggregator. Renders one section per [RemovalTimeline.Group] and clusters by upgrade-guide section.
 */
abstract class RemovalReportAggregationWorkAction : WorkAction<RemovalReportAggregationParameter> {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(RemovalReportAggregationWorkAction::class.java.name) as Logger
        const val GITHUB_BASE_URL = "https://github.com/gradle/gradle/blob"

        /** Pseudo-team used in the Team column/filter for paths with no CODEOWNERS owner. */
        const val UNOWNED = "(unowned)"

        // Client-side Platform-team + Project filters (both must match): hides non-matching rows and any
        // marker-block/group that ends up empty, and keeps the (count) badges in the headings/notes in sync.
        private val FILTER_SCRIPT = """
    <script>
        (function () {
            var teamSel = document.getElementById('filter-team');
            var projectSel = document.getElementById('filter-project');
            var empty = document.getElementById('empty');
            function apply() {
                var team = teamSel.value;
                var project = projectSel.value;
                var totalVisible = 0;
                document.querySelectorAll('section.group').forEach(function (group) {
                    var groupVisible = 0;
                    group.querySelectorAll('.marker-block').forEach(function (block) {
                        var blockVisible = 0;
                        block.querySelectorAll('tr[data-project]').forEach(function (tr) {
                            var matchProject = !project || tr.getAttribute('data-project') === project;
                            var matchTeam = !team || tr.getAttribute('data-teams').split(' ').indexOf(team) !== -1;
                            var show = matchProject && matchTeam;
                            tr.style.display = show ? '' : 'none';
                            if (show) { blockVisible++; }
                        });
                        block.style.display = blockVisible ? '' : 'none';
                        var bc = block.querySelector('.marker-note .count');
                        if (bc) { bc.textContent = blockVisible; }
                        groupVisible += blockVisible;
                    });
                    group.style.display = groupVisible ? '' : 'none';
                    var gc = group.querySelector('h2 .count');
                    if (gc) { gc.textContent = groupVisible; }
                    totalVisible += groupVisible;
                });
                if (empty) { empty.hidden = totalVisible !== 0; }
            }
            teamSel.addEventListener('change', apply);
            projectSel.addEventListener('change', apply);
            apply();
        })();
    </script>
""".trimIndent()
    }

    override fun execute() {
        val currentCommit = parameters.currentCommit.get()
        val codeOwners = CodeOwners.parse(parameters.codeownersFile.get().asFile)
        val entries = mutableListOf<Entry>()
        parameters.reports.files.sorted().forEach { file ->
            val project = file.nameWithoutExtension
            file.forEachLine(Charsets.UTF_8) { line ->
                if (line.isBlank()) return@forEachLine
                parseLine(line)?.let { record ->
                    entries.add(
                        Entry(
                            timeline = record.timeline,
                            kind = record.kind,
                            symbol = record.symbol,
                            guideMajor = record.guideMajor,
                            guideSection = record.guideSection,
                            project = project,
                            teams = codeOwners.teamsFor(record.relativePath),
                            url = githubUrl(record.relativePath, record.lineNumber, currentCommit)
                        )
                    )
                }
            }
        }

        val targetMajor = parameters.targetMajorVersion.get()
        generateHtmlReport(entries, targetMajor)
        LOGGER.lifecycle("Generated Gradle $targetMajor removal html report to file://${parameters.htmlReportFile.get().asFile.absolutePath}")
        generateCsvReport(entries, targetMajor)
        LOGGER.lifecycle("Generated Gradle $targetMajor removal csv report to file://${parameters.csvReportFile.get().asFile.absolutePath}")
    }

    /** Parses a `timeline;kind;guideMajor;guideSection;relativePath;lineNumber;symbol` record (symbol last). */
    private
    fun parseLine(line: String): Record? {
        val parts = line.split(';', limit = 7)
        if (parts.size < 7) {
            LOGGER.warn("Skipping malformed removal report record: $line")
            return null
        }
        val timeline = RemovalTimeline.fromString(parts[0]) ?: return null
        return Record(
            timeline = timeline,
            kind = RemovalKind.fromString(parts[1]),
            guideMajor = parts[2].toIntOrNull(),
            guideSection = parts[3].ifBlank { null },
            relativePath = parts[4],
            lineNumber = parts[5].toIntOrNull() ?: -1,
            symbol = parts[6]
        )
    }

    private
    fun githubUrl(relativePath: String, lineNumber: Int, currentCommit: String) =
        "$GITHUB_BASE_URL/$currentCommit/$relativePath#L$lineNumber".replace(" ", "%20")

    private
    fun generateHtmlReport(entries: List<Entry>, targetMajor: Int) {
        val outputFile = parameters.htmlReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        val currentCommit = parameters.currentCommit.get()
        val csvFileName = parameters.csvReportFile.get().asFile.name
        val projectOptions = entries.map { it.project }.distinct().sorted()
            .joinToString("\n") { "                <option value=\"${it.escape()}\">${it.escape()}</option>" }
        // "(unowned)" sorts last so the real teams stay together at the top of the dropdown.
        val teamOptions = entries.flatMap { it.teamTokens() }.distinct()
            .sortedWith(compareBy({ it == UNOWNED }, { it }))
            .joinToString("\n") { "                <option value=\"${it.escape()}\">${it.escape()}</option>" }
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println(
                """<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Gradle $targetMajor removals</title>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
    <meta content="width=device-width, initial-scale=1" name="viewport">
    <link type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">
    <style>
        body { font-family: Lato, sans-serif; margin: 0 auto; max-width: 1200px; padding: 1rem 1.5rem 4rem; }
        table { border-collapse: collapse; width: 100%; font-size: .88rem; margin-bottom: .5rem; }
        th, td { text-align: left; border-bottom: 1px solid #e0e0e0; padding: .45rem .6rem; vertical-align: top; }
        th { border-bottom: 2px solid #c0c0c0; }
        code { word-break: break-word; }
        h2 { margin-top: 2rem; }
        .meta { color: #6b6f76; font-size: .9rem; }
        .filters { margin: 1rem 0; }
        .filters select { padding: .4rem .5rem; border: 1px solid #c0c0c0; border-radius: 6px; font-size: .9rem; }
        .marker-note { margin: 1rem 0 .35rem; color: #44484d; }
        .marker-note code { background: #f1f1f4; padding: .05rem .35rem; border-radius: 4px; }
        .empty { color: #6b6f76; font-style: italic; margin: 1rem 0; }
        .nogu { color: #b06f00; }
        .dynamic { color: #6b6f76; font-style: italic; }
        a { color: #1ba0c4; }
    </style>
</head>
<body>
    <h1>Gradle $targetMajor removals</h1>
    <p class="meta">${entries.size} deprecation call site(s) across the build &middot; source commit <code>${currentCommit.take(10)}</code> &middot; data: <a href="$csvFileName">$csvFileName</a></p>
    <div class="filters">
        <label>Platform team
            <select id="filter-team">
                <option value="">all teams</option>
$teamOptions
            </select>
        </label>
        <label>Project
            <select id="filter-project">
                <option value="">all projects</option>
$projectOptions
            </select>
        </label>
    </div>
    <p class="empty" id="empty" hidden>No deprecations for the selected filters.</p>
"""
            )
            // One table per marker. Within a single-marker table the marker is constant, so it moves to a
            // note above the table instead of a redundant column; groups with several markers get one table each.
            RemovalTimeline.Group.entries.forEach { group ->
                val groupEntries = entries.filter { it.timeline.group == group }
                if (groupEntries.isEmpty()) return@forEach
                writer.println("<section class=\"group\">")
                writer.println("<h2>${group.displayName} (<span class=\"count\">${groupEntries.size}</span>)</h2>")
                RemovalTimeline.entries.filter { it.group == group }.forEach { marker ->
                    val markerEntries = groupEntries.filter { it.timeline == marker }
                    if (markerEntries.isEmpty()) return@forEach
                    writer.println("<div class=\"marker-block\">")
                    writer.println("<p class=\"marker-note\">Marker <code>${marker.method(targetMajor)}</code> — ${marker.description(targetMajor)} (<span class=\"count\">${markerEntries.size}</span>)</p>")
                    writer.println("<table><thead><tr><th>Deprecated symbol</th><th>Kind</th><th>Upgrade guide</th><th>Platform team</th><th>Project</th><th>Source</th></tr></thead><tbody>")
                    markerEntries
                        .sortedWith(compareBy({ it.guideSection ?: "~" }, { it.project }, { it.symbol }))
                        .forEach { writer.println(renderRow(it)) }
                    writer.println("</tbody></table>")
                    writer.println("</div>")
                }
                writer.println("</section>")
            }
            writer.println(FILTER_SCRIPT)
            writer.println("</body></html>")
        }
    }

    private
    fun renderRow(e: Entry): String {
        val symbolCell = if (e.symbol.startsWith("<dynamic")) {
            "<span class=\"dynamic\">${e.symbol.escape()}</span>"
        } else {
            "<code>${e.symbol.escape()}</code>"
        }
        val guideCell = if (e.guideSection != null && e.guideMajor != null) {
            "<a href=\"${upgradeGuideUrl(e.guideMajor, e.guideSection)}\">${e.guideMajor}: ${e.guideSection.escape()}</a>"
        } else {
            "<span class=\"nogu\">none</span>"
        }
        val tokens = e.teamTokens()
        val teamCell = if (e.teams.isEmpty()) "<span class=\"nogu\">$UNOWNED</span>" else tokens.joinToString(", ") { it.escape() }
        val project = e.project.escape()
        val dataTeams = tokens.joinToString(" ") { it.escape() }
        return "<tr data-project=\"$project\" data-teams=\"$dataTeams\">" +
            "<td>$symbolCell</td><td>${e.kind.name.lowercase()}</td>" +
            "<td>$guideCell</td><td>$teamCell</td><td>$project</td>" +
            "<td><a href=\"${e.url}\">source</a></td></tr>"
    }

    private
    fun generateCsvReport(entries: List<Entry>, targetMajor: Int) {
        val outputFile = parameters.csvReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println("Group;Marker;Kind;Symbol;UpgradeGuideMajor;UpgradeGuideSection;PlatformTeams;Project;Link")
            entries
                .sortedWith(compareBy({ it.timeline.group.ordinal }, { it.guideSection ?: "~" }, { it.project }, { it.symbol }))
                .forEach { e ->
                    writer.println(
                        listOf(
                            e.timeline.group.displayName,
                            e.timeline.method(targetMajor),
                            e.kind.name.lowercase(),
                            "\"${e.symbol.replace("\"", "\"\"")}\"",
                            e.guideMajor?.toString() ?: "",
                            e.guideSection ?: "",
                            e.teamTokens().joinToString(" "),
                            e.project,
                            e.url
                        ).joinToString(";")
                    )
                }
        }
    }

    private
    fun Entry.teamTokens(): List<String> = teams.ifEmpty { listOf(UNOWNED) }

    private
    fun String.escape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}


private
data class Record(
    val timeline: RemovalTimeline,
    val kind: RemovalKind,
    val guideMajor: Int?,
    val guideSection: String?,
    val relativePath: String,
    val lineNumber: Int,
    val symbol: String
)


private
data class Entry(
    val timeline: RemovalTimeline,
    val kind: RemovalKind,
    val symbol: String,
    val guideMajor: Int?,
    val guideSection: String?,
    val project: String,
    val teams: List<String>,
    val url: String
)
