/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.build

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.composite.internal.DefaultIncludedBuild
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.Path


class ConfigurationCacheIncludedBuildState(
    buildIdentifier: BuildIdentifier,
    identityPath: Path,
    buildDefinition: BuildDefinition,
    isImplicit: Boolean,
    owner: BuildState,
    buildTree: BuildTreeState,
    buildLifecycleControllerFactory: BuildLifecycleControllerFactory,
    projectStateRegistry: ProjectStateRegistry,
    instantiator: Instantiator
) : DefaultIncludedBuild(buildIdentifier, identityPath, buildDefinition, isImplicit, owner, buildTree, buildLifecycleControllerFactory, projectStateRegistry, instantiator)
