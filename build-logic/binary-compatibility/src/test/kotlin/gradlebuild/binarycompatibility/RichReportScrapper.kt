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
package gradlebuild.binarycompatibility

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File


internal
fun scrapeRichReport(richReportFile: File): RichReport =

    Jsoup.parse(richReportFile, "UTF-8").run {
        RichReport(
            scrapeMessagesForSeverity("error"),
            scrapeMessagesForSeverity("warning"),
            scrapeMessagesForSeverity("info"),
            scrapeMessagesForSeverity("accepted")
        )
    }


private
fun Document.scrapeMessagesForSeverity_OLD(severity: String): List<String> =

    select("tr.severity-$severity").map { tr ->
        tr.select("td")[1]
            .select("span")
            .text()
            .substringBefore(" If you did this intentionally")
    }


private
fun Document.scrapeMessagesForSeverity(severity: String): List<ReportMessage> =

    select("tr.severity-$severity").map { tr ->
        val entry = tr.select("td")[1]
        ReportMessage(
            entry.select("span")
                .text().substringBefore(" If you did this intentionally"),
            entry.select("ul li")
                .map { it.text() }

        )
    }


internal
data class ReportMessage(
    val message: String,
    val details: List<String>
)


internal
data class RichReport(
    val errors: List<ReportMessage>,
    val warnings: List<ReportMessage>,
    val information: List<ReportMessage>,
    val accepted: List<ReportMessage>
) {

    val isEmpty: Boolean
        get() = errors.isEmpty() && warnings.isEmpty() && information.isEmpty()

    fun toText() =
        StringBuilder("Binary compatibility\n").apply {
            listOf("Errors" to errors, "Warnings" to warnings, "Information" to information, "Accepted" to accepted).forEach { (name, list) ->
                if (list.isNotEmpty()) {
                    append("  $name (").append(list.size).append(")\n")
                    append(list.joinToString(separator = "\n    ", prefix = "    ", postfix = "\n"))
                }
            }
        }.toString()
}
