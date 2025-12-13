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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.serialization.Cached

import org.gradle.kotlin.dsl.accessors.ProjectSchemaProvider
import org.gradle.kotlin.dsl.accessors.SchemaType
import org.gradle.kotlin.dsl.accessors.TypeAccessibility
import org.gradle.kotlin.dsl.accessors.TypeAccessibilityProvider
import org.gradle.kotlin.dsl.accessors.TypedProjectSchema
import org.gradle.kotlin.dsl.accessors.accessorsFor
import org.gradle.kotlin.dsl.accessors.fragmentsFor
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider

import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject


@DisableCachingByDefault(because = "Produces only non-cacheable console output")
abstract class PrintAccessors : DefaultTask() {

    init {
        group = "help"
        description = "Prints the Kotlin code for accessing the currently available project extensions."
    }

    @get:Inject
    protected
    abstract val projectSchemaProvider: ProjectSchemaProvider

    private
    val accessorsSource = Cached.of {
        val classpath = classPathProvider.compilationClassPathOf((project as ProjectInternal).classLoaderScope)
        TypeAccessibilityProvider(classpath).use { accessibilityProvider ->
            accessorsSourceFor(schemaOf(project)!!, accessibilityProvider::accessibilityForType)
        }
    }

    @get:Inject
    protected
    abstract val classPathProvider: KotlinScriptClassPathProvider

    @Suppress("unused")
    @TaskAction
    internal
    fun printExtensions() {
        println(accessorsSource.get())
    }

    private
    fun schemaOf(project: Project) =
        projectSchemaProvider.schemaFor(project, (project as ProjectInternal).classLoaderScope)
}


internal
fun accessorsSourceFor(
    schema: TypedProjectSchema,
    typeAccessibilityMapper: (SchemaType) -> TypeAccessibility
): String = buildString {
    for (sourceFragment in accessorSourceFragmentsFor(schema, typeAccessibilityMapper)) {
        appendLine()
        appendLine(sourceFragment.replaceIndent("    "))
        appendLine()
    }
}


private
fun accessorSourceFragmentsFor(
    schema: TypedProjectSchema,
    typeAccessibilityMapper: (SchemaType) -> TypeAccessibility
): Sequence<String> = accessorsFor(schema.map(typeAccessibilityMapper)).flatMap { accessor ->
    val (_, fragments) = fragmentsFor(accessor)
    fragments
        .map { it.source }
        .filter { it.isNotBlank() }
}
