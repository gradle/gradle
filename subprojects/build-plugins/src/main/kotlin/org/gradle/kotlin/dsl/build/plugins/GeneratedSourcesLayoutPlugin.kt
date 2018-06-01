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

package org.gradle.kotlin.dsl.build.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet

import org.gradle.kotlin.dsl.*

import javax.inject.Inject


internal
class GeneratedSourcesLayoutPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        extensions.create(
            "generatedSourcesLayout",
            GeneratedSourcesLayout::class.java,
            project)
    }
}


internal
open class GeneratedSourcesLayout @Inject constructor(
    project: Project
) {

    val sourcesBaseDir = project.layout.directoryProperty(project.layout.buildDirectory.dir("generated-sources"))
    val resourcesBaseDir = project.layout.directoryProperty(project.layout.buildDirectory.dir("generated-resources"))

    fun sourcesOutputDirFor(sourceSet: SourceSet, identifier: String): Provider<Directory> =
        outputDirFor(sourcesBaseDir, sourceSet, identifier)

    fun resourcesOutputDirFor(sourceSet: SourceSet, identifier: String): Provider<Directory> =
        outputDirFor(resourcesBaseDir, sourceSet, identifier)

    private
    fun outputDirFor(baseDir: DirectoryProperty, sourceSet: SourceSet, identifier: String): Provider<Directory> =
        baseDir.dir("${sourceSet.name}/$identifier")
}


internal
val Project.generatedSourcesLayout
    get() = the<GeneratedSourcesLayout>()
