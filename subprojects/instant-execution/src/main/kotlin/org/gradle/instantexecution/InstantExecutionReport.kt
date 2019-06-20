/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.PropertyWarning

import org.gradle.internal.logging.ConsoleRenderer

import org.gradle.reporting.HtmlReportRenderer
import org.gradle.reporting.ReportRenderer

import java.io.File


class InstantExecutionReport(

    private
    val warnings: List<PropertyWarning>,

    private
    val outputDirectory: File

) {

    private
    val uniquePropertyWarnings = warnings.groupBy {
        propertyDescriptionFor(it) to it.message
    }

    private
    val indexFileName = "index.html"

    val summary: String
        get() = StringBuilder().apply {
            appendln("${uniquePropertyWarnings.size} instant execution issues found:")
            uniquePropertyWarnings.keys.forEach { (property, message) ->
                append("  - ")
                append(property)
                append(": ")
                appendln(message)
            }
            appendln("See the complete report at ${clickableUrlFor(indexFile)}")
        }.toString()

    fun writeReportFile() {
        outputDirectory.mkdirs()
        }
        HtmlReportRenderer().render(
            Unit,
            renderer {
                renderRawHtmlPage(indexFileName, Unit, renderer {
                    output.run {
                        val baseCssLink = requireResource(getResource("/org/gradle/reporting/base-style.css"))
                        writeHeader(baseCssLink)
                        writeReport()
                        writeFooter()
                    }
                })
            },
            outputDirectory
        )
    }

    private
    fun getResource(path: String) = javaClass.getResource(path)

    private
    fun Appendable.writeHeader(baseCssLink: String?) = append("""
<!doctype html>
<html lang="en">
  <head>
    <!-- Required meta tags -->
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">

    <!-- Bootstrap CSS -->
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css" integrity="sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T" crossorigin="anonymous">
    
    <title>Instant Execution Failures</title>
</head>
  <body>
    """)

    private
    fun Appendable.writeFooter() = append("""
    <!-- Optional JavaScript -->
    <!-- jQuery first, then Popper.js, then Bootstrap JS -->
    <script src="https://code.jquery.com/jquery-3.3.1.slim.min.js" integrity="sha384-q8i/X+965DzO0rT7abK41JStQIAqVgRVzpbzo5smXKp4YfRvH+8abtTE1Pi6jizo" crossorigin="anonymous"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" integrity="sha384-UO2eT0CpHqdSJQ6hJty5KVphtPhzWj9WO1clHTMGa3JDZwrnQq4sF86dIHNDz0W1" crossorigin="anonymous"></script>
    <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js" integrity="sha384-JjSmVgyd0p3pXB1rRibZUAYoIIy6OrQ6VrjIEaFf/nJGzIxFDsf4x0xIM+B07jRM" crossorigin="anonymous"></script>
  </body>
</html>
    """)

    private
    fun Appendable.writeReport() {
        val h1 = tag("h1")
        val ul = tag("ul")
        val li = tag("li")
        h1 {
            appendln("${uniquePropertyWarnings.size} issues found")
        }
        ul {
            uniquePropertyWarnings.forEach { (summary, warnings) ->
                val (property, message) = summary
                li {
                    appendln("$property - $message")
                    ul {
                        warnings.forEach { warning ->
                            li {
                                append(traceStringOf(warning))
                            }
                        }
                    }
                }
            }
        }
    }

    private
    fun traceStringOf(warning: PropertyWarning): String {
        return warning.trace.sequence.toList().reversed().joinToString(separator = " / ") {
            when (it) {
                is PropertyTrace.Bean -> it.type.name
                is PropertyTrace.Property -> it.name
                is PropertyTrace.Task -> "task ${it.path} / ${it.type.name}"
                else -> it.toString()
            }
        }
    }

    private
    fun tag(name: String): (Appendable.(() -> Unit) -> Unit) {
        val open = "<$name>"
        val close = "</$name>"
        return { body ->
            appendln(open)
            body()
            appendln(close)
        }
    }

    private
    fun propertyDescriptionFor(warning: PropertyWarning): String = warning.trace.run {
        when (this) {
            is PropertyTrace.Property -> {
                when (kind) {
                    PropertyKind.Field -> "field '${typeFrom(trace).name}.$name'"
                    else -> "$kind '$name' of '${taskPathFrom(trace)}'"
                }
            }
            else -> toString()
        }
    }

    private
    fun taskPathFrom(trace: PropertyTrace): String =
        trace.sequence.filterIsInstance<PropertyTrace.Task>().first().path

    private
    fun typeFrom(trace: PropertyTrace): Class<*> =
        trace.sequence.mapNotNull {
            when (it) {
                is PropertyTrace.Bean -> it.type
                is PropertyTrace.Task -> it.type
                else -> null
            }
        }.first()

    private
    val indexFile
        get() = outputDirectory.resolve(indexFileName)
}


internal
fun <T, O> renderer(render: O.(T) -> Unit): ReportRenderer<T, O> =
    object : ReportRenderer<T, O>() {
        override fun render(model: T, output: O) {
            output.render(model)
        }
    }


private
fun clickableUrlFor(file: File) = ConsoleRenderer().asClickableFileUrl(file)
