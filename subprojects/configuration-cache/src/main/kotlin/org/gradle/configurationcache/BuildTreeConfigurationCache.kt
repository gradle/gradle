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

import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scopes.BuildTree::class)
interface BuildTreeConfigurationCache {
    /**
     * Loads the scheduled tasks from cache, if available, or else runs the given function to schedule the tasks and then
     * writes the result to cache.
     */
    fun loadOrScheduleRequestedTasks(scheduler: () -> Unit)

    /**
     * Prepares to load or create a model. Does nothing if the cached model is available or else prepares to capture
     * configuration fingerprints and validation problems and then runs the given function.
     */
    fun maybePrepareModel(action: () -> Unit)

    /**
     * Loads the cached model, if available, or else runs the given function to create it and then writes the result to cache.
     */
    fun <T : Any> loadOrCreateModel(creator: () -> T): T

    // This is a temporary property to allow migration from a root build scoped cache to a build tree scoped cache
    val isLoaded: Boolean

    // This is a temporary method to allow migration from a root build scoped cache to a build tree scoped cache
    fun attachRootBuild(host: DefaultConfigurationCache.Host)
}
