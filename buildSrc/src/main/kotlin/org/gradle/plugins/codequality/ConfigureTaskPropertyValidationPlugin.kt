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
package org.gradle.plugins.codequality

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.plugin.devel.tasks.ValidateTaskProperties

import accessors.*

import org.gradle.kotlin.dsl.*


private
const val validateTaskName = "validateTaskProperties"


open class ConfigureTaskPropertyValidationPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        plugins.withType<JavaBasePlugin> {
            validateTaskPropertiesForConfiguration(configurations["compile"])
        }

        plugins.withType<JavaLibraryPlugin> {
            validateTaskPropertiesForConfiguration(configurations["api"])
        }
    }
}


private
fun Project.validateTaskPropertiesForConfiguration(configuration: Configuration) {

    // Apply to all projects depending on :core
    // TODO Add a comment on why those projects.
    val coreProject = project(":core")

    if (this == coreProject) {
        addValidateTask()
    } else {
        configuration.dependencies.withType<ProjectDependency>().matching { it.dependencyProject == coreProject }.all {
            addValidateTask()
        }
    }
}


private
fun Project.addValidateTask() {
    val reportFileName = "task-properties/report.txt"
    afterEvaluate {
        // This block gets called twice for the core project as core applies the base as well as the library plugin. That is why we need to check
        // whether the task already exists.
        if (tasks.findByName(validateTaskName) != null) {
            tasks.create<ValidateTaskProperties>(validateTaskName) {
                val mainSourceSet = java.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
                dependsOn(mainSourceSet.output)
                classes = mainSourceSet.output.classesDirs
                classpath = mainSourceSet.runtimeClasspath
                // TODO Should we provide a more intuitive way in the task definition to configure this property from Kotlin?
                outputFile.set(reporting.baseDirectory.file(reportFileName))
                failOnWarning = true
            }
        }
    }
}
