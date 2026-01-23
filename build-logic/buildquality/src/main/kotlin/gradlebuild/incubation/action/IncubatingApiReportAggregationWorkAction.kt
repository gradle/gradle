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
    }

    override fun execute() {
        val byCategory = mutableMapOf<String, ProjectNameToProblems>()
        parameters.reports.files.sorted().forEach { file ->
            file.forEachLine(Charsets.UTF_8) {
                val (version, _, problem, relativePath, lineNumber) = it.split(';')
                byCategory.getOrPut(toCategory(version, file.nameWithoutExtension)) {
                    mutableMapOf()
                }.getOrPut(file.nameWithoutExtension) {
                    mutableSetOf()
                }.add(Problem(name = problem, relativePath = relativePath, lineNumber = lineNumber.toInt()))
            }
        }
        generateHtmlReport(byCategory)
        LOGGER.lifecycle("Generated incubating html report report to file://${parameters.htmlReportFile.get().asFile.absolutePath}")

        generateCsvReport(byCategory)
        LOGGER.lifecycle("Generated incubating csv report to file://${parameters.csvReportFile.get().asFile.absolutePath}")
    }

    private
    fun toCategory(version: String, gradleModule: String) = when {
        gradleModule.endsWith("-native") || gradleModule in listOf("model-core", "platform-base", "testing-base") -> "Software Model and Native"
        else -> "Incubating since $version"
    }

    private
    fun generateHtmlReport(data: Map<String, ProjectNameToProblems>) {
        val outputFile = parameters.htmlReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        
        // Collect all problems from all categories and projects
        val allProblems = mutableListOf<Problem>()
        data.values.forEach { projectsWithProblems ->
            projectsWithProblems.values.forEach { problems ->
                allProblems.addAll(problems)
            }
        }
        
        // Sort all problems alphabetically by name
        val sortedProblems = allProblems.sortedBy { it.name }
        
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
       <p>Total incubating APIs: ${sortedProblems.size}</p>
       <ul>
    """
            )

            sortedProblems.forEach { problem ->
                val javadocLink = toJavadocLink(problem.name)
                if (javadocLink != null) {
                    writer.println("   <li><a href=\"$javadocLink\">${problem.name.escape()}</a></li>")
                } else {
                    writer.println("   <li>${problem.name.escape()}</li>")
                }
            }
            
            writer.println("""
       </ul>
    </body>
</html>""")
        }
    }
    
    private
    fun toJavadocLink(name: String): String? {
        // Extract the fully qualified class name from the API name
        // Examples:
        // "org.gradle.api.SomeClass" -> "https://docs.gradle.org/current/javadoc/org/gradle/api/SomeClass.html"
        // "org.gradle.api.SomeClass.someMethod()" -> "https://docs.gradle.org/current/javadoc/org/gradle/api/SomeClass.html#someMethod()"
        
        if (!name.startsWith("org.gradle.")) {
            return null
        }
        
        val methodIndex = name.indexOf('(')
        val hasMethod = methodIndex != -1
        
        return if (hasMethod) {
            // Extract class name and method signature
            val beforeMethod = name.substring(0, methodIndex)
            val lastDotBeforeMethod = beforeMethod.lastIndexOf('.')
            if (lastDotBeforeMethod == -1) return null
            
            val className = beforeMethod.substring(0, lastDotBeforeMethod)
            val methodSignature = name.substring(lastDotBeforeMethod + 1)
            
            val classPath = className.replace('.', '/')
            "https://docs.gradle.org/current/javadoc/$classPath.html#$methodSignature"
        } else {
            // Just a class name
            val classPath = name.replace('.', '/')
            "https://docs.gradle.org/current/javadoc/$classPath.html"
        }
    }

    private
    fun generateCsvReport(data: Map<String, ProjectNameToProblems>) {
        val outputFile = parameters.csvReportFile.get().asFile
        val currentCommit = parameters.currentCommit.get()
        outputFile.parentFile.mkdirs()
        outputFile.printWriter(Charsets.UTF_8).use { writer ->
            writer.println("Link;Platform/Subproject;Incubating since")
            data.toSortedMap().forEach { (category, projectsWithProblems) ->
                projectsWithProblems.forEach { (project, problems) ->
                    problems.forEach {
                        val url = "$GITHUB_BASE_URL/$currentCommit/${it.relativePath}#L${it.lineNumber}".urlEncodeSpace()
                        writer.println("=HYPERLINK(\"$url\",\"${it.name}\");$project;${category.removePrefix("Incubating since ")};")
                    }
                }
            }
        }
    }

    private
    fun String.urlEncodeSpace() = replace(" ", "%20")

    private
    fun String.escape() = replace("<", "&lt;").replace(">", "&gt;")
}

data class Problem(
    val name: String,
    val relativePath: String,
    val lineNumber: Int
)

typealias ProjectNameToProblems = MutableMap<String, MutableSet<Problem>>
