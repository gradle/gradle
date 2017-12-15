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
package org.gradle.plugins.classycle

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*

import accessors.java
import accessors.reporting


internal
val classycleBaseName = "classycle"


@Suppress("unused")
open class ClassyclePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val extension = extensions.create(classycleBaseName, ClassycleExtension::class.java, project)
        configurations.create(classycleBaseName)
        dependencies.add(classycleBaseName, "classycle:classycle:1.4@jar")
        tasks {
            val classycle by creating
            java.sourceSets.all {
                val taskName = getTaskName(classycle.name, null)
                val sourceSetTask = createTask(taskName, Classycle::class) {
                    classesDirs = output.classesDirs
                    excludePatterns.set(extension.excludePatterns)
                    reportName = name
                    reportDir = reporting.file(classycle.name)
                    reportResourcesZip.set(extension.reportResourcesZip)
                    dependsOn(output)
                }
                classycle.dependsOn(sourceSetTask)
                "check" { dependsOn(sourceSetTask) }
                "codeQuality" { dependsOn(sourceSetTask) }
            }
        }
    }
}
