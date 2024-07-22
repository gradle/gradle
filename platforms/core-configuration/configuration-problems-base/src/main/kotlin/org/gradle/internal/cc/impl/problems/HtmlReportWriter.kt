/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

import java.io.Writer


/**
 * Writes the configuration cache html report.
 *
 * The report is laid out in such a way as to allow extracting the pure JSON model
 * by looking for the `// begin-report-data` and `// end-report-data` markers.
 */
class HtmlReportWriter(
    private val writer: Writer,
    private val htmlTemplate: HtmlReportTemplate,
    val jsonModelWriter: JsonModelWriter
) {

    fun beginHtmlReport() {
        writer.append(htmlTemplate.header)
        beginReportData()
        jsonModelWriter.beginModel()
    }

    fun endHtmlReport(details: JsonSource) {
        jsonModelWriter.endModel(details)
        endReportData()
        writer.append(htmlTemplate.footer)
    }

    private
    fun beginReportData() {
        writer.run {
            appendLine("""<script type="text/javascript">""")
            appendLine("function configurationCacheProblems() { return (")
            appendLine("// begin-report-data")
        }
    }

    private
    fun endReportData() {
        writer.run {
            appendLine()
            appendLine("// end-report-data")
            appendLine(");}")
            appendLine("</script>")
        }
    }

    fun close() {
        writer.close()
    }
}
