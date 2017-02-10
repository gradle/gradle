/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.accessors

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.script.lang.kotlin.codegen.fileHeader

import java.io.BufferedWriter
import java.io.File

import kotlin.text.Regex.Companion.escape


open class ProcessProjectSchema : org.gradle.api.DefaultTask() {

    @get:InputFile
    var inputSchema: java.io.File? = null

    @get:OutputDirectory
    var destinationDir: java.io.File? = null

    @Suppress("unused")
    @TaskAction
    fun processProjectSchema() {
        loadMultiProjectSchemaFrom(inputSchema!!).forEach { (projectPath, projectSchema) ->
            projectAccessorsFileFor(projectPath).bufferedWriter().use { writer ->
                writeAccessorsFor(projectSchema, writer)
            }
        }
    }

    private
    fun writeAccessorsFor(projectSchema: ProjectSchema<String>, writer: BufferedWriter) {
        writer.apply {
            write(fileHeader)
            newLine()
            appendln("import org.gradle.api.Project")
            appendln("import org.gradle.script.lang.kotlin.*")
            newLine()
            projectSchema.forEachAccessor {
                appendln(it)
            }
        }
    }

    private
    fun projectAccessorsFileFor(projectPath: String) =
        projectAccessorsFileFor(projectPath, destinationDir!!)
}


internal
fun projectAccessorsFileFor(projectPath: String, baseDir: java.io.File) =
    java.io.File(destinationDirFor(projectPath, baseDir), "${uniqueFileNamePrefixFrom(projectPath)}_accessors.kt")


private
fun uniqueFileNamePrefixFrom(projectPath: String) =
    projectPath.replace(invalidPathCharsRegex, "_")


private
val invalidPathChars = arrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')


private
val invalidPathCharsRegex = Regex(invalidPathChars.joinToString(separator = "|") { escape(it.toString()) })


private
fun destinationDirFor(projectPath: String, baseDir: java.io.File) =
    projectPath
        .split(":")
        .filter(String::isNotBlank)
        .fold(baseDir, ::File)
