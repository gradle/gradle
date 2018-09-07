/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.SortedSet


open class GradleDistribution(project: Project, gradleHomeDir: DirectoryProperty) {

    private
    val libs: ConfigurableFileTree = project.fileTree(gradleHomeDir.dir("lib")).apply {
        include("*.jar")
        exclude("plugins/**")
    }

    private
    val plugins: ConfigurableFileTree = project.fileTree(gradleHomeDir.dir("lib")).apply {
        include("*.jar")
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val staticContent: ConfigurableFileTree = project.fileTree(gradleHomeDir).apply {
        exclude("lib/**")
        exclude("samples/**")
        exclude("src/**")
        exclude("docs/**")
        exclude("getting-started.html")
    }

    @get:Classpath
    val coreJars: SortedSet<File>
        get() = libs.files.toSortedSet()

    @get:Classpath
    val pluginJars: SortedSet<File>
        get() = plugins.files.toSortedSet()
}
