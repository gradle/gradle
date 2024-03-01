/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import java.util.function.Function


class ConfigurationCacheAwareLocalComponentCache(
    private val cache: BuildTreeConfigurationCache
) : LocalComponentCache {
    override fun computeIfAbsent(project: ProjectState, factory: Function<ProjectState, LocalComponentGraphResolveState>): LocalComponentGraphResolveState {
        return cache.loadOrCreateProjectMetadata(project.identityPath) {
            factory.apply(project)
        }
    }
}
