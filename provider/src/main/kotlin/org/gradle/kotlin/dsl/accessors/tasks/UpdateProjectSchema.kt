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

package org.gradle.kotlin.dsl.accessors.tasks

import groovy.json.JsonOutput.prettyPrint

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.accessors.*

import java.io.File


open class UpdateProjectSchema : DefaultTask() {

    init {
        group = "Build Setup"
        description = "Generates Kotlin accessors for accessing and configuring the currently available project extensions and conventions."
    }

    @Suppress("unused")
    @Input
    fun getSchemaInput(): Map<String, Any> = schema

    @get:OutputFile
    var destinationFile: File = project.file(PROJECT_SCHEMA_RESOURCE_PATH)

    private
    val schema by lazy {
        multiProjectKotlinStringSchemaFor(project)
    }

    @Suppress("unused")
    @TaskAction
    fun generateProjectSchema() {
        destinationFile.writeText(prettyPrint(toJson(schema)))
    }
}
