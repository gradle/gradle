/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.execution.plan.Node
import org.gradle.normalization.internal.InputNormalizationHandlerInternal
import org.gradle.util.Path
import java.io.File


/**
 * State cached for a project.
 */
internal
sealed class CachedProjectState(
    val path: Path,
    val projectDir: File,
    val buildFile: File
)


internal
class ProjectWithWork(
    path: Path,
    projectDir: File,
    buildFile: File,
    val buildDir: File,
    val normalizationState: InputNormalizationHandlerInternal.CachedState?
) : CachedProjectState(path, projectDir, buildFile)


internal
class ProjectWithNoWork(
    path: Path,
    projectDir: File,
    buildFile: File
) : CachedProjectState(path, projectDir, buildFile)


data class BuildToStore(
    val build: VintageGradleBuild,
    // Does this build have work scheduled?
    val hasWork: Boolean,
    // Does this build have a child build with work scheduled?
    val hasChildren: Boolean
) {
    fun hasChildren() = BuildToStore(build, hasWork, true)
}


/**
 * State cached for a build in the tree.
 */
internal
sealed class CachedBuildState(
    val identityPath: Path,
)


/**
 * A build in the tree whose projects were loaded. May or may not have work scheduled.
 */
internal
sealed class BuildWithProjects(
    identityPath: Path,
    val rootProjectName: String,
    val projects: List<CachedProjectState>
) : CachedBuildState(identityPath)


/**
 * A build in the tree with work scheduled.
 */
internal
class BuildWithWork(
    identityPath: Path,
    val build: ConfigurationCacheBuild,
    rootProjectName: String,
    projects: List<CachedProjectState>,
    val workGraph: List<Node>
) : BuildWithProjects(identityPath, rootProjectName, projects)


/**
 * A build in the tree with no work scheduled.
 */
internal
class BuildWithNoWork(
    identityPath: Path,
    rootProjectName: String,
    projects: List<ProjectWithNoWork>
) : BuildWithProjects(identityPath, rootProjectName, projects)


/**
 * A build in the tree whose projects were not loaded. Has no work as a result.
 */
internal
class BuildWithNoProjects(
    identityPath: Path
) : CachedBuildState(identityPath)
