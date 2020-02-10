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
package org.gradle.gradlebuild.buildquality

import accessors.java
import accessors.reporting

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.testing.Test

import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.tasks.ValidatePlugins


private
const val validateTaskName = "validatePlugins"


private
const val reportFileName = "task-properties/report.txt"


open class TaskPropertyValidationPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        afterEvaluate {
            // This is in an after evaluate block to defer the decision until after the `java-gradle-plugin` may have been applied, so as to not collide with it
            // It would be better to use some convention plugins instead, that apply a fixes set of plugins (including this one)
            if (plugins.hasPlugin("java-base")) {
                val validationRuntime by configurations.creating {
                    isCanBeConsumed = false
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    // Required to select the right :distributions variant
                    attributes {
                        attribute(Attribute.of("org.gradle.runtime", String::class.java), "minimal")
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
                    }
                }
                dependencies.add(
                    validationRuntime.name,
                    dependencies.project(":distributions")
                )

                val validateTask = if (plugins.hasPlugin("java-gradle-plugin")) {
                    tasks.named(validateTaskName, ValidatePlugins::class)
                } else {
                    tasks.register(validateTaskName, ValidatePlugins::class)
                }

                validateTask {
                    configureValidateTask(validationRuntime)
                }
                tasks.named("codeQuality") {
                    dependsOn(validateTask)
                }
                tasks.withType(Test::class).configureEach {
                    shouldRunAfter(validateTask)
                }
            }
        }
    }

    private
    fun ValidatePlugins.configureValidateTask(validationRuntime: Configuration) {
        val main by project.java.sourceSets
        dependsOn(main.output)
        classes.setFrom(main.output.classesDirs)
        classpath.from(main.output) // to pick up resources too
        classpath.from(main.runtimeClasspath)
        classpath.from(validationRuntime)
        // TODO Should we provide a more intuitive way in the task definition to configure this property from Kotlin?
        outputFile.set(project.reporting.baseDirectory.file(reportFileName))
        failOnWarning.set(true)
        enableStricterValidation.set(true)
    }
}
