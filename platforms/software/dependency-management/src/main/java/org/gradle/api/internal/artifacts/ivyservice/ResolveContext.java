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

import org.gradle.api.Describable;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.internal.model.ModelContainer;

import java.util.List;

/**
 * Represents something that can be resolved.
 * <p>
 * Resolve contexts expose parameters defining what and how to resolve, and may implement
 * side effect hooks and interceptors to react to resolution events.
 */
public interface ResolveContext extends Describable {

    /**
     * Get model owning the mutable state that backs this resolution.
     * <p>
     * The model exposed by this method will be locked when performing dependency resolution,
     * calling resolution hooks and building resolution parameters/context.
     */
    ModelContainer<?> getModel();

    /**
     * Get the legacy non-thread-safe parameters.
     */
    LegacyResolutionParameters getLegacyResolutionParameters();

    /**
     * Get the thread-safe resolution parameters.
     *
     * @param forCompleteGraph true if these parameters are for resolving the complete graph,
     * false if they are for resolving the partial graph for build dependencies.
     */
    ResolutionParameters getResolutionParameters(boolean forCompleteGraph);

    /**
     * Get the repositories used as a source of external dependencies.
     */
    List<ResolutionAwareRepository> getRepositories();

    /**
     * Hook to be executed before resolution has started.
     * <p>
     * This is to be used to support the {@link DependencyResolutionListener#beforeResolve(ResolvableDependencies)} listener.
     * We should avoid introducing new APIs that expose side effects like this.
     */
    default void beforeResolve() {
        // No side effects by default.
    }

    /**
     * Hook to be executed after resolution has completed.
     * <p>
     * This is to be used to support the {@link DependencyResolutionListener#afterResolve(ResolvableDependencies)} listener.
     * We should avoid introducing new APIs that expose side effects like this.
     */
    default void afterResolve(ResolverResults results) {
        // No side effects by default.
    }

    /**
     * Allows the resolution process to be intercepted.
     * Useful for wrapping the resolution in a build operation.
     * <p>
     * To continue with resolution, call {@link ResolutionAction#proceed(LegacyResolutionParameters, ResolutionParameters, List)}.
     */
    default ResolverResults interceptResolution(
        LegacyResolutionParameters legacyParams,
        ResolutionParameters params,
        List<ResolutionAwareRepository> repositories,
        ResolutionAction resolution
    ) {
        return resolution.proceed(legacyParams, params, repositories);
    }

    /**
     * An action that causes graph resolution to proceed.
     */
    interface ResolutionAction {

        /**
         * Called to proceed with resolution.
         */
        ResolverResults proceed(
            LegacyResolutionParameters legacyParams,
            ResolutionParameters params,
            List<ResolutionAwareRepository> repositories
        );

    }

}
