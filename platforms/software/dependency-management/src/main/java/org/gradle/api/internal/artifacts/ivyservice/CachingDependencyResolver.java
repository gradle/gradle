/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.internal.Describables;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.work.WorkerThreadRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Performs dependency resolution for a {@link ResolveContext}, using a
 * delegate {@link ShortCircuitingResolutionExecutor}, caching the results.
 * <p>
 * This class is thread-safe.
 */
public class CachingDependencyResolver {

    private final ResolveContext resolveContext;

    private volatile CalculatedValue<ResolverResults> buildDependenciesResults;
    private final CalculatedValue<ResolverResults> completeResults;

    private final ReentrantLock stateLock = new ReentrantLock();
    private boolean executedBeforeResolveHook = false;
    private boolean executedAfterResolveHook = false;

    public CachingDependencyResolver(
        ResolveContext resolveContext,
        WorkerThreadRegistry workerThreadRegistry,
        ShortCircuitingResolutionExecutor resolutionExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.resolveContext = resolveContext;

        this.completeResults = calculatedValueContainerFactory.create(Describables.of("complete results"), context ->
            withMutableState(resolveContext, workerThreadRegistry, () ->
                resolveContext.interceptResolution(
                    resolveContext.getLegacyResolutionParameters(),
                    resolveContext.getResolutionParameters(true),
                    resolveContext.getRepositories(),
                    resolutionExecutor::resolveGraph
                )
            )
        );

        this.buildDependenciesResults = calculatedValueContainerFactory.create(Describables.of("build dependency results"), context ->
            withMutableState(resolveContext, workerThreadRegistry, () ->
                resolutionExecutor.resolveBuildDependencies(
                    resolveContext.getLegacyResolutionParameters(),
                    resolveContext.getResolutionParameters(false),
                    completeResults
                )
            )
        );
    }

    /**
     * Ensures the resolve context's mutable model is locked, then executes the given action.
     */
    private static <T> T withMutableState(ResolveContext resolveContext, WorkerThreadRegistry workerThreadRegistry, Supplier<T> action) {
        if (!resolveContext.getModel().hasMutableState()) {
            if (!workerThreadRegistry.isWorkerThread()) {
                // Error if we are executing in a user-managed thread.
                throw new IllegalStateException(resolveContext.getDisplayName() + " was resolved from a thread not managed by Gradle.");
            } else {
                DeprecationLogger.deprecateBehaviour("Resolution of " + resolveContext.getDisplayName() + " from a context different than the project context.")
                    .willBecomeAnErrorInGradle9()
                    .withUserManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
                    .nagUser();
                return resolveContext.getModel().fromMutableState(p -> action.get());
            }
        }

        return action.get();
    }

    /**
     * Returns the current state of the resolution, or null if the resolution is not yet complete.
     * <p>
     * Returns null if resolution has started but not yet completed. May return non-null even if the
     * {@link ResolveContext#afterResolve(ResolverResults)} hook has not yet been called.
     */
    @Nullable
    public ResolverResults getState() {
        if (completeResults.isFinalized()) {
            return completeResults.get();
        } else if (buildDependenciesResults.isFinalized()) {
            return buildDependenciesResults.get();
        } else {
            return null;
        }
    }

    /**
     * Resolve the graph partially, for build dependencies, caching the result.
     *
     * @see ShortCircuitingResolutionExecutor#resolveBuildDependencies(LegacyResolutionParameters, ResolutionParameters, CalculatedValue)
     */
    public ResolverResults resolveBuildDependencies() {
        buildDependenciesResults.finalizeIfNotAlready();
        return buildDependenciesResults.get();
    }

    /**
     * Resolve the complete graph, caching the result.
     *
     * @see ShortCircuitingResolutionExecutor#resolveGraph(LegacyResolutionParameters, ResolutionParameters, List)
     */
    public ResolverResults resolveGraph() {
        if (!executedBeforeResolveHook) {
            stateLock.lock();
            try {
                if (!executedBeforeResolveHook) {
                    executedBeforeResolveHook = true;
                    resolveContext.beforeResolve();
                }
            } finally {
                stateLock.unlock();
            }
        }

        completeResults.finalizeIfNotAlready();
        ResolverResults results = completeResults.get();

        // Complete results have been calculated. From now on, use the full results
        // for build dependencies as well.
        buildDependenciesResults = completeResults;

        if (!executedAfterResolveHook) {
            stateLock.lock();
            try {
                if (!executedAfterResolveHook) {
                    executedAfterResolveHook = true;
                    resolveContext.afterResolve(results);
                }
            } finally {
                stateLock.unlock();
            }
        }

        return results;
    }

}
