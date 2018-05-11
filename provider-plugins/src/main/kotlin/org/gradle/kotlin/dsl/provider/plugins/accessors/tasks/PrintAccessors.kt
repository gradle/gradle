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

package org.gradle.kotlin.dsl.provider.plugins.accessors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.provider.spi.AccessorsProvider
import org.gradle.kotlin.dsl.provider.spi.ProjectSchemaProvider
import org.gradle.kotlin.dsl.provider.spi.TypeAccessibility
import org.gradle.kotlin.dsl.provider.spi.serviceOf
import org.gradle.kotlin.dsl.provider.spi.withKotlinTypeStrings


open class PrintAccessors : DefaultTask() {

    init {
        group = "help"
        description = "Prints the Kotlin code for accessing the currently available project extensions and conventions."
    }

    @Suppress("unused")
    @TaskAction
    fun printExtensions() {
        projectSchemaProvider.schemaFor(project).withKotlinTypeStrings().map(::accessible).let { accessibleSchema ->
            accessorsProvider.forEachAccessorOf(accessibleSchema) {
                println()
                println(it)
                println()
            }
        }
    }

    private
    val projectSchemaProvider: ProjectSchemaProvider
        get() = project.serviceOf()

    private
    val accessorsProvider: AccessorsProvider
        get() = project.serviceOf()

    private
    fun accessible(type: String): TypeAccessibility =
        TypeAccessibility.Accessible(type)
}
