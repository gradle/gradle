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

package org.gradle.gradlebuild.packaging

import accessors.java
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.WriteProperties
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register


/**
 * Generates Gradle API metadata resources.
 *
 * Include and exclude patterns for the Gradle API.
 * Parameter names for the Gradle API.
 */
open class ApiMetadataPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        val extension = extensions.create("apiMetadata", ApiMetadataExtension::class, project)

        val apiDeclarationTask =
                tasks.register("apiDeclarationResource", WriteProperties::class) {
                    property("includes", extension.includes.get().joinToString(":"))
                    property("excludes", extension.excludes.get().joinToString(":"))
                    outputFile = generatedPropertiesFileFor(apiDeclarationFilename).get().asFile
                }

        val apiParameterNamesTask =
                tasks.register("apiParameterNamesResource", ParameterNamesResourceTask::class) {
                    sources.from(extension.sources.asFileTree.matching {
                        include(extension.includes.get())
                        exclude(extension.excludes.get())
                    })
                    destinationFile.set(generatedPropertiesFileFor(apiParametersFilename))
                }

        java.sourceSets["main"].output.dir(mapOf("builtBy" to apiDeclarationTask), generatedDirFor(apiDeclarationFilename))
        java.sourceSets["main"].output.dir(mapOf("builtBy" to apiParameterNamesTask), generatedDirFor(apiParametersFilename))
    }

    private
    fun Project.generatedDirFor(name: String) =
            layout.buildDirectory.dir("generated-resources/$name")

    private
    fun Project.generatedPropertiesFileFor(name: String) =
            layout.buildDirectory.file("generated-resources/$name/$name.properties")

    private
    val apiDeclarationFilename = "gradle-api-declaration"

    private
    val apiParametersFilename = "gradle-api-parameter-names"
}
