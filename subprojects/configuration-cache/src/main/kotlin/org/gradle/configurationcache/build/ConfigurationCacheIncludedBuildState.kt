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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.composite.internal.DefaultIncludedBuild
import org.gradle.initialization.DefaultGradleLauncher
import org.gradle.internal.build.BuildState
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.util.Path


open class ConfigurationCacheIncludedBuildState(
    buildIdentifier: BuildIdentifier,
    identityPath: Path,
    buildDefinition: BuildDefinition,
    isImplicit: Boolean,
    owner: BuildState,
    parentLease: WorkerLeaseRegistry.WorkerLease
) : DefaultIncludedBuild(buildIdentifier, identityPath, buildDefinition, isImplicit, owner, parentLease) {

    init {
        (gradleLauncher as DefaultGradleLauncher).setConfiguredByCache()
    }

    override fun loadSettings(): SettingsInternal =
        gradle.settings

    override fun getConfiguredBuild(): GradleInternal =
        gradle

    override fun addTasks(taskPaths: MutableIterable<String>) =
        throw UnsupportedOperationException("Cannot add tasks ${taskPaths.toList()} to included build loaded from the cache.")

    override fun scheduleTasks(tasks: MutableIterable<String>) =
        Unit
}
