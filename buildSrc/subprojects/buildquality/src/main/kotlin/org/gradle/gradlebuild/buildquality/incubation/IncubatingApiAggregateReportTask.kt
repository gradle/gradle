/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.buildquality.incubation

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject


@CacheableTask
open class IncubatingApiAggregateReportTask
    @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @Internal
    var reports: Map<String, File>? = null

    @get:Nested
    val reportFiles
        get() = reports?.mapValues { IncubatingApiReportFile(it.value) }

    @OutputFile
    val htmlReportFile = project.objects.fileProperty().also {
        it.set(project.layout.buildDirectory.file("reports/incubation/all-incubating.html"))
    }

    @TaskAction
    fun generateReport() {
        workerExecutor.submit(GenerateReport::class.java) {
            isolationMode = IsolationMode.NONE
            params(reports, htmlReportFile.asFile.get())
        }
    }
}


data class IncubatingApiReportFile(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val file: File
)


typealias ReportNameToProblems = MutableMap<String, MutableSet<String>>


open class GenerateReport @Inject constructor(private val reports: Map<String, File>, private val outputFile: File) : Runnable {
    override
    fun run() {
        val byVersion = mutableMapOf<String, ReportNameToProblems>()
        reports.forEach { name, file ->
            file.forEachLine(Charsets.UTF_8) {
                val (version, _, problem) = it.split(';')
                byVersion.getOrPut(version) {
                    mutableMapOf()
                }.getOrPut(name) {
                    mutableSetOf()
                }.add(problem)
            }
        }
        generateReport(byVersion)
    }

    private
    fun generateReport(data: Map<String, ReportNameToProblems>) {
        outputFile.parentFile.mkdirs()
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println("""<html lang="en">
    <head>
       <META http-equiv="Content-Type" content="text/html; charset=UTF-8">
       <title>Incubating APIs</title>
       <link xmlns:xslthl="http://xslthl.sf.net" rel="stylesheet" href="https://fonts.googleapis.com/css?family=Lato:400,400i,700">
       <meta xmlns:xslthl="http://xslthl.sf.net" content="width=device-width, initial-scale=1" name="viewport">
       <link xmlns:xslthl="http://xslthl.sf.net" type="text/css" rel="stylesheet" href="https://docs.gradle.org/current/userguide/base.css">

    </head>
    <body>
       <h1>Incubating APIs</h1>
       <h2>Index</h2>
       <ul>
    """)

            data.toSortedMap().forEach { version, _ ->
                writer.println("<li><a href=\"#$version\">Incubating since $version</a><br></li>")
            }
            writer.println("</ul>")
            data.toSortedMap().forEach { version, problems ->
                writer.println("<a name=\"$version\"></a>")
                writer.println("<h2>Incubating since $version</h2>")
                problems.forEach { name, issues ->
                    writer.println("<h3>In $name</h3>")
                    writer.println("<ul>")
                    issues.forEach {
                        writer.println("   <li>${it.escape()}</li>")
                    }
                    writer.println("</ul>")
                }
            }
            writer.println("</body></html>")
        }
    }

    private
    fun String.escape() = replace("<", "&lt;").replace(">", "&gt;")
}
