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

package org.gradle.script.lang.kotlin.buildsrc

import groovy.json.JsonOutput.prettyPrint
import groovy.json.JsonOutput.toJson

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.configuration.project.ProjectConfigureAction

import org.gradle.script.lang.kotlin.task

import java.io.File


class ProjectExtensionsTaskRegistrationAction : ProjectConfigureAction {

    override fun execute(project: ProjectInternal) {
        with (project) {
            if (this == rootProject) {
                task<GenerateProjectSchema>("gskGenerateExtensions") {
                    destinationFile = file("buildSrc/${ProjectExtensionsBuildSrcConfigurationAction.PROJECT_SCHEMA_RESOURCE_PATH}")
                }
            }
        }
    }
}


open class GenerateProjectSchema : DefaultTask() {

    @get:OutputFile
    var destinationFile: File? = null

    @Suppress("unused")
    @TaskAction
    fun act() {
        val schema = project.allprojects.map { it.path to schemaFor(it) }.toMap()
        destinationFile!!.writeText(
            prettyPrint(toJson(schema)))
    }

    private fun schemaFor(project: Project) =
        mapOf(
            "extensions" to project.extensions.schema.mapValues { kotlinTypeStringFor(it.value) },
            "conventions" to project.convention.plugins.mapValues { kotlinTypeStringFor(it.value.javaClass) })

    private fun kotlinTypeStringFor(clazz: Class<*>) =
        clazz.kotlin.qualifiedName!!
}
