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

import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path


@ServiceScope(Scopes.BuildTree::class)
interface BuildTreeConfigurationCache {
    /**
     * Determines whether the cache entry can be loaded or needs to be stored or updated.
     */
    fun initializeCacheEntry()

    /**
     * Loads the scheduled tasks from cache, if available, or else runs the given function to schedule the tasks and then
     * writes the result to the cache.
     */
    fun loadOrScheduleRequestedTasks(graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?, scheduler: (BuildTreeWorkGraph) -> BuildTreeWorkGraph.FinalizedGraph): WorkGraphResult

    /**
     * Loads the scheduled tasks from cache.
     */
    fun loadRequestedTasks(graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?): BuildTreeWorkGraph.FinalizedGraph

    /**
     * Prepares to load or create a model. Does nothing if the cached model is available or else prepares to capture
     * configuration fingerprints and validation problems and then runs the given function.
     */
    fun maybePrepareModel(action: () -> Unit)

    /**
     * Loads the cached model, if available, or else runs the given function to create it and then writes the result to the cache.
     */
    fun <T : Any> loadOrCreateModel(creator: () -> T): T

    /**
     * Loads a cached intermediate model, if available, or else runs the given function to create it and then writes the result to the cache.
     *
     * @param identityPath The project for which the model should be created, or null for a build scoped model.
     */
    fun <T> loadOrCreateIntermediateModel(identityPath: Path?, modelName: String, creator: () -> T?): T?

    /**
     * Loads cached dependency resolution metadata for the given project, if available, or else runs the given function to create it and then writes the result to the cache.
     */
    fun loadOrCreateProjectMetadata(identityPath: Path, creator: () -> LocalComponentGraphResolveState): LocalComponentGraphResolveState

    /**
     * Flushes any remaining state to the cache and closes any resources
     */
    fun finalizeCacheEntry()

    // This is a temporary property to allow migration from a root build scoped cache to a build tree scoped cache
    val isLoaded: Boolean

    // This is a temporary method to allow migration from a root build scoped cache to a build tree scoped cache
    fun attachRootBuild(host: DefaultConfigurationCache.Host)

    class WorkGraphResult(val graph: BuildTreeWorkGraph.FinalizedGraph, val wasLoadedFromCache: Boolean, val entryDiscarded: Boolean)
}
