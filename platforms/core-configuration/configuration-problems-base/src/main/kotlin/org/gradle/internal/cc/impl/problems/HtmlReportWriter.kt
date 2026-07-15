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
 * Writes the configuration cache / problems html report.
 *
 * The model is emitted as a generated `configurationCacheProblems()` function that assembles the
 * report object at load time: the diagnostics are streamed into a `const diagnostics` array as they
 * arrive, and the surrounding model (the "envelope") is appended last, once its totals are known.
 *
 * To keep the two pieces readable as plain JSON (e.g. by the integration test fixture), each is wrapped in its
 * own marker pair:
 * - the diagnostics array between `// begin-report-diagnostics`/`// end-report-diagnostics`
 * - the envelope object between `// begin-report-model`/`// end-report-model`.
 *
 * Array elements are comma-separated, so each marked region is valid JSON on its own.
 * The whole region remains delimited by the outer `// begin-report-data`/`// end-report-data` markers.
 */
class HtmlReportWriter(
    private val writer: Writer,
    private val htmlTemplate: HtmlReportTemplate
) {

    private
    var firstDiagnostic = true

    fun beginHtmlReport() {
        writer.append(htmlTemplate.header)
        writer.run {
            appendLine("""<script type="text/javascript">""")
            appendLine("function configurationCacheProblems() {")
            appendLine("// begin-report-data")
            appendLine("const diagnostics =")
            appendLine("// begin-report-diagnostics")
            appendLine("[")
        }
    }

    /**
     * Appends one already-serialized diagnostic to the streamed `diagnostics` array.
     */
    fun writeDiagnostic(diagnosticJson: String) {
        writer.run {
            if (!firstDiagnostic) {
                appendLine(",")
            }
            firstDiagnostic = false
            append(diagnosticJson)
        }
    }

    fun endHtmlReport(envelopeJson: String) {
        writer.run {
            appendLine()
            appendLine("]")
            appendLine("// end-report-diagnostics")
            appendLine(";")
            appendLine("const report =")
            appendLine("// begin-report-model")
            appendLine(envelopeJson)
            appendLine("// end-report-model")
            appendLine(";")
            appendLine("report.diagnostics = diagnostics;")
            appendLine("return report;")
            appendLine("// end-report-data")
            appendLine("}")
            appendLine("</script>")
        }
        writer.append(htmlTemplate.footer)
    }

    fun close() {
        writer.close()
    }
}
