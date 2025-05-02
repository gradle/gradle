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

import org.gradle.workers.WorkAction


abstract class IncubatingApiReportAggregationWorkAction : WorkAction<IncubatingApiReportAggregationParameter> {
    override fun execute() {
        val byCategory = mutableMapOf<String, ReportNameToProblems>()
        parameters.reports.files.sorted().forEach { file ->
            file.forEachLine(Charsets.UTF_8) {
                val (version, _, problem) = it.split(';')
                byCategory.getOrPut(toCategory(version, file.nameWithoutExtension)) {
                    mutableMapOf()
                }.getOrPut(file.nameWithoutExtension) {
                    mutableSetOf()
                }.add(problem)
            }
        }
        generateReport(byCategory)
    }

    private
    fun toCategory(version: String, gradleModule: String) = when {
        gradleModule.endsWith("-native") || gradleModule in listOf("model-core", "platform-base", "testing-base") -> "Software Model and Native"
        else -> "Incubating since $version"
    }

    private
    fun generateReport(data: Map<String, ReportNameToProblems>) {
        val outputFile = parameters.htmlReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println(
                """<html lang="en">
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
    """
            )

            data.toSortedMap().forEach { (category, _) ->
                writer.println("<li><a href=\"#$category\">$category</a><br></li>")
            }
            writer.println("</ul>")
            data.toSortedMap().forEach { (category, problems) ->
                writer.println("<a name=\"$category\"></a>")
                writer.println("<h2>$category</h2>")
                problems.forEach { (name, issues) ->
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


typealias ReportNameToProblems = MutableMap<String, MutableSet<String>>
