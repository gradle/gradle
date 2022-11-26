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
    val buildDir: File
)


internal
class ProjectWithWork(
    path: Path,
    projectDir: File,
    buildDir: File,
    val normalizationState: InputNormalizationHandlerInternal.CachedState?
) : CachedProjectState(path, projectDir, buildDir)


internal
class ProjectWithNoWork(
    path: Path,
    projectDir: File,
    buildDir: File
) : CachedProjectState(path, projectDir, buildDir)


/**
 * State cached for a build in the tree.
 */
internal
data class CachedBuildState(
    val build: ConfigurationCacheBuild,
    val projects: List<CachedProjectState>,
    val workGraph: List<Node>,
    val children: List<CachedBuildState>
)
