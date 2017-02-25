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

import org.gradle.api.Project
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.script.lang.kotlin.codegen.fileHeader
import org.gradle.script.lang.kotlin.resolver.serviceOf

import java.io.BufferedWriter
import java.io.File

import kotlin.text.Regex.Companion.escape


open class ProcessProjectSchema : org.gradle.api.DefaultTask() {

    @get:InputFile
    var inputSchema: File? = null

    @get:OutputDirectory
    var destinationDir: File? = null

    @Suppress("unused")
    @TaskAction
    fun processProjectSchema() {
        loadMultiProjectSchemaFrom(inputSchema!!).forEach { (projectPath, projectSchema) ->
            writeAccessorsFor(projectPath, projectSchema, destinationDir!!)
        }
    }
}


private
fun writeAccessorsFor(projectPath: String, projectSchema: ProjectSchema<String>, destinationDir: File): File =
    projectAccessorsFileFor(projectPath, destinationDir)
        .apply { parentFile.mkdirs() }
        .apply {
            bufferedWriter().use { writer ->
                writeAccessorsFor(projectSchema, writer)
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


internal
fun additionalSourceFilesForBuildscriptOf(project: Project): List<File> =
    projectAccessorsFileFor(project).let {
        when {
            it.isFile -> listOf(it)
            else -> automaticAccessorsSourcesFor(project)
        }
    }


private
fun automaticAccessorsSourcesFor(project: Project): List<File> =
    listOf(
        writeAccessorsFor(
            project.path,
            schemaFor(project).withKotlinTypeStrings(),
            temporaryAccessorsDirectoryFor(project)))


private
fun temporaryAccessorsDirectoryFor(project: Project) =
    temporaryFileProviderOf(project).createTemporaryDirectory("gradle-script-kotlin", "accessors")


private
fun temporaryFileProviderOf(project: Project) =
    project.serviceOf<TemporaryFileProvider>()



private
fun projectAccessorsFileFor(project: Project) =
    projectAccessorsFileFor(project.path, accessorDirFor(project))


private
fun accessorDirFor(project: Project) =
    project.rootProject.file("buildSrc/${ProjectExtensionsBuildSrcConfigurationAction.PROJECT_ACCESSORS_DIR}")


internal
fun projectAccessorsFileFor(projectPath: String, baseDir: File) =
    File(destinationDirFor(projectPath, baseDir), "${uniqueFileNamePrefixFrom(projectPath)}_accessors.kt")


private
fun uniqueFileNamePrefixFrom(projectPath: String) =
    projectPath.replace(invalidPathCharsRegex, "_")


private
val invalidPathChars = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')


private
val invalidPathCharsRegex = Regex(invalidPathChars.joinToString(separator = "|") { escape(it.toString()) })


private
fun destinationDirFor(projectPath: String, baseDir: File) =
    projectPath
        .split(":")
        .filter(String::isNotBlank)
        .fold(baseDir, ::File)
