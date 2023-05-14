/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.build.BuildState
import org.gradle.util.Path
import java.io.File


interface ConfigurationCacheBuild {

    val gradle: GradleInternal

    val state: BuildState

    fun registerRootProject(rootProjectName: String, projectDir: File, buildDir: File)

    fun registerProject(projectPath: Path, dir: File, buildDir: File)

    fun getProject(path: String): ProjectInternal

    // Creates all registered projects for this build
    fun createProjects()

    fun addIncludedBuild(buildDefinition: BuildDefinition, settingsFile: File?, buildPath: Path): ConfigurationCacheBuild

    fun getBuildSrcOf(ownerId: BuildIdentifier): ConfigurationCacheBuild
}
