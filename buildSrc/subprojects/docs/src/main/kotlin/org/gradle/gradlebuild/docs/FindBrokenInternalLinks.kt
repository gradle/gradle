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

package org.gradle.gradlebuild.docs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File


@CacheableTask
open class FindBrokenInternalLinks : DefaultTask() {
    companion object {
        val linkPattern = Regex("<<([a-zA-Z_0-9-]+[.]adoc)#([^,>]*),[^>]+>")
    }

    @get:InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val inputDirectory = project.objects.directoryProperty()

    @get:OutputFile
    val reportFile = project.objects.fileProperty().convention {
        project.layout.buildDirectory.dir("reports").map {
            it.file("dead-internal-links.txt")
        }.get().asFile
    }

    @TaskAction
    fun checkDeadLinks() {
        val baseDir = inputDirectory.get()
        val errors = mutableMapOf<String, List<Error>>()
        baseDir
            .asFileTree
            .matching {
                include("*.adoc")
            }
            .forEach {
                hasDeadLink(baseDir.asFile, it, errors)
            }
        reportErrors(errors)
    }

    private
    fun reportErrors(errors: MutableMap<String, List<Error>>) {
        if (errors.isEmpty()) {
            reportFile.get().asFile.writeText("All clear!")
            return
        }
        val messageBuilder = StringBuilder()
        errors.toSortedMap().forEach { (file, errorsForFile) ->
            messageBuilder.append("In $file:\n")
            errorsForFile.forEach {
                messageBuilder.append("   - At line ${it.line}, invalid include ${it.missingFile}\n")
            }
        }
        reportFile.get().asFile.writeText(messageBuilder.toString())
        throw GradleException("Found invalid internal links. See ${reportFile.get().asFile}")
    }

    private
    fun hasDeadLink(baseDir: File, sourceFile: File, errors: MutableMap<String, List<Error>>) {
        var lineNumber = 0
        val errorsForFile = mutableListOf<Error>()
        sourceFile.forEachLine { line ->
            lineNumber++
            linkPattern.findAll(line).forEach {
                val fileName = it.groupValues[1]
                val referencedFile = File(baseDir, fileName)
                if (!referencedFile.exists()) {
                    errorsForFile.add(Error(lineNumber, fileName))
                } else {
                    val idName = it.groupValues[2]
                    if (idName.isNotEmpty()) {
                        if (!referencedFile.readText().contains("[[" + idName + "]]")) {
                            errorsForFile.add(Error(lineNumber, fileName + " " + idName))
                        }
                    }
                }
            }
        }
        if (errorsForFile.isNotEmpty()) {
            errors.put(sourceFile.name, errorsForFile)
        }
    }

    data class Error(val line: Int, val missingFile: String)
}
