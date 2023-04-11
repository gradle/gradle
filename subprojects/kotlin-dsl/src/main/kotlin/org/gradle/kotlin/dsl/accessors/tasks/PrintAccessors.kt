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

package org.gradle.kotlin.dsl.accessors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.serialization.Cached

import org.gradle.kotlin.dsl.accessors.ProjectSchemaProvider
import org.gradle.kotlin.dsl.accessors.TypedProjectSchema
import org.gradle.kotlin.dsl.accessors.accessible
import org.gradle.kotlin.dsl.accessors.accessorsFor
import org.gradle.kotlin.dsl.accessors.fragmentsFor

import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject


@DisableCachingByDefault(because = "Produces only non-cacheable console output")
abstract class PrintAccessors : DefaultTask() {

    init {
        group = "help"
        description = "Prints the Kotlin code for accessing the currently available project extensions and conventions."
    }

    @get:Inject
    protected
    abstract val projectSchemaProvider: ProjectSchemaProvider

    private
    val schema = Cached.of { schemaOf(project) }

    @Suppress("unused")
    @TaskAction
    internal
    fun printExtensions() {
        printAccessorsFor(schema.get())
    }

    private
    fun schemaOf(project: Project) =
        projectSchemaProvider.schemaFor(project)
}


internal
fun printAccessorsFor(schema: TypedProjectSchema) {
    for (sourceFragment in accessorSourceFragmentsFor(schema)) {
        println()
        println(sourceFragment.replaceIndent("    "))
        println()
    }
}


private
fun accessorSourceFragmentsFor(schema: TypedProjectSchema): Sequence<String> =
    accessorsFor(schema.map(::accessible)).flatMap { accessor ->
        val (_, fragments) = fragmentsFor(accessor)
        fragments
            .map { it.source }
            .filter { it.isNotBlank() }
    }
