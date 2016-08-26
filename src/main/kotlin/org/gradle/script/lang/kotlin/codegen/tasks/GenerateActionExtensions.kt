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

package org.gradle.script.lang.kotlin.codegen.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.script.lang.kotlin.codegen.ActionExtensionWriter
import org.gradle.script.lang.kotlin.codegen.MarkdownKDocProvider
import org.gradle.script.lang.kotlin.codegen.classNodeFor

import org.gradle.script.lang.kotlin.codegen.forEachZipEntryIn
import org.gradle.script.lang.kotlin.codegen.isApiClassEntry

import java.io.File

open class GenerateActionExtensions : DefaultTask() {

    /**
     * Generated Kotlin file.
     */
    @get:OutputFile
    var outputFile: File? = null

    /**
     * Markdown documentation source.
     */
    @get:InputFile
    var docSource: File? = null

    @get:InputFile
    val gradleApiJar: File by lazy {
        (project.dependencies.gradleApi() as SelfResolvingDependency)
            .resolve()
            .single { it.name.startsWith("gradle-api-") }
    }

    @TaskAction
    fun generate() {
        outputFile!!.bufferedWriter().use { writer ->
            val extensionWriter = ActionExtensionWriter(writer, kDocProvider())
            forEachZipEntryIn(gradleApiJar) {
                if (isApiClassEntry()) {
                    val classNode = classNodeFor(zipInputStream)
                    extensionWriter.writeExtensionsFor(classNode)
                }
            }
        }
    }

    private fun kDocProvider() =
        docSource?.let { MarkdownKDocProvider.from(it.readText()) }
}

