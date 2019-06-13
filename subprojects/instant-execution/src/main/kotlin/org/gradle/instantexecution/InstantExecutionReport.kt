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

import java.io.BufferedWriter
import java.io.File


class InstantExecutionReport(

    warnings: List<PropertyWarning>,

    private
    val reportFile: File

) {

    private
    val uniquePropertyWarnings = warnings.groupBy {
        propertyDescriptionFor(it) to it.message
    }

    val summary: String
        get() = StringBuilder().apply {
            appendln("${uniquePropertyWarnings.size} instant execution issues found:")
            uniquePropertyWarnings.keys.forEach { (property, message) ->
                append("  - ")
                append(property)
                append(": ")
                appendln(message)
            }
            appendln("See the complete report at ${clickableUrlFor(reportFile)}")
        }.toString()

    fun writeReportFile() {
        reportFile.bufferedWriter().use { writer ->
            writer.writeReport()
        }
    }

    private
    fun BufferedWriter.writeReport() {
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
                                val reversedTrace = warning.trace.sequence.toList().reversed()
                                append(
                                    reversedTrace.joinToString(separator = " / ") {
                                        when (it) {
                                            is PropertyTrace.Bean -> it.type.name
                                            is PropertyTrace.Property -> it.name
                                            is PropertyTrace.Task -> "task ${it.path} / ${it.type.name}"
                                            else -> it.toString()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
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
}


private
fun clickableUrlFor(file: File) = ConsoleRenderer().asClickableFileUrl(file)
