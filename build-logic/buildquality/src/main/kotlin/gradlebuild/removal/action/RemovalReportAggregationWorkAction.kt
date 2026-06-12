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
    }

    override fun execute() {
        val currentCommit = parameters.currentCommit.get()
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
                            url = githubUrl(record.relativePath, record.lineNumber, currentCommit)
                        )
                    )
                }
            }
        }

        generateHtmlReport(entries)
        LOGGER.lifecycle("Generated Gradle 10 removal html report to file://${parameters.htmlReportFile.get().asFile.absolutePath}")
        generateCsvReport(entries)
        LOGGER.lifecycle("Generated Gradle 10 removal csv report to file://${parameters.csvReportFile.get().asFile.absolutePath}")
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
    fun generateHtmlReport(entries: List<Entry>) {
        val outputFile = parameters.htmlReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        val currentCommit = parameters.currentCommit.get()
        val csvFileName = parameters.csvReportFile.get().asFile.name
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println(
                """<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Gradle 10 removals</title>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
    <meta content="width=device-width, initial-scale=1" name="viewport">
    <link type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">
    <style>
        body { font-family: Lato, sans-serif; margin: 0 auto; max-width: 1200px; padding: 1rem 1.5rem 4rem; }
        table { border-collapse: collapse; width: 100%; font-size: .88rem; }
        th, td { text-align: left; border-bottom: 1px solid #e0e0e0; padding: .45rem .6rem; vertical-align: top; }
        th { border-bottom: 2px solid #c0c0c0; }
        code { word-break: break-word; }
        h2 { margin-top: 2rem; }
        .meta { color: #6b6f76; font-size: .9rem; }
        .nogu { color: #b06f00; }
        .dynamic { color: #6b6f76; font-style: italic; }
    </style>
</head>
<body>
    <h1>Gradle 10 removals</h1>
    <p class="meta">${entries.size} deprecation call site(s) across the build &middot; source commit <code>${currentCommit.take(10)}</code> &middot; data: <a href="$csvFileName">$csvFileName</a></p>
"""
            )
            RemovalTimeline.Group.entries.forEach { group ->
                val inGroup = entries.filter { it.timeline.group == group }
                if (inGroup.isEmpty()) return@forEach
                writer.println("<h2>${group.displayName} (${inGroup.size})</h2>")
                writer.println("<table><thead><tr><th>Deprecated symbol</th><th>Kind</th><th>Marker</th><th>Upgrade guide</th><th>Project</th><th>Source</th></tr></thead><tbody>")
                inGroup
                    .sortedWith(compareBy({ it.guideSection ?: "~" }, { it.project }, { it.symbol }))
                    .forEach { e ->
                        val symbolCell = if (e.symbol.startsWith("<dynamic")) "<span class=\"dynamic\">${e.symbol.escape()}</span>" else "<code>${e.symbol.escape()}</code>"
                        val guideCell = e.guideSection?.let { "${e.guideMajor}: $it" } ?: "<span class=\"nogu\">none</span>"
                        writer.println("<tr><td>$symbolCell</td><td>${e.kind.name.lowercase()}</td><td>${e.timeline.method}</td><td>$guideCell</td><td>${e.project}</td><td><a href=\"${e.url}\">source</a></td></tr>")
                    }
                writer.println("</tbody></table>")
            }
            writer.println("</body></html>")
        }
    }

    private
    fun generateCsvReport(entries: List<Entry>) {
        val outputFile = parameters.csvReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println("Group;Marker;Kind;Symbol;UpgradeGuideMajor;UpgradeGuideSection;Project;Link")
            entries
                .sortedWith(compareBy({ it.timeline.group.ordinal }, { it.guideSection ?: "~" }, { it.project }, { it.symbol }))
                .forEach { e ->
                    writer.println(
                        listOf(
                            e.timeline.group.displayName,
                            e.timeline.method,
                            e.kind.name.lowercase(),
                            "\"${e.symbol.replace("\"", "\"\"")}\"",
                            e.guideMajor?.toString() ?: "",
                            e.guideSection ?: "",
                            e.project,
                            e.url
                        ).joinToString(";")
                    )
                }
        }
    }

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
    val url: String
)
