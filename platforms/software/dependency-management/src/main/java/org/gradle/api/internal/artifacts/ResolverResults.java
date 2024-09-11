/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.specs.Spec;

/**
 * Immutable representation of the state of dependency resolution. Can represent the result of resolving build
 * dependencies or the result of a full dependency graph resolution.
 * <p>
 * In case of failures, both fatal and partial, exceptions are attached to the {@link VisitedGraphResults}.
 */
public interface ResolverResults {
    /**
     * Returns the old model, which has been replaced by {@link VisitedGraphResults} and {@link VisitedArtifactSet}.
     *
     * <strong>This method should only be used to implement existing legacy public API methods.</strong>
     */
    LegacyResolverResults getLegacyResults();

    /**
     * Return the model representing the resolved graph. This model provides access
     * to the root component as well as any failure that occurred while resolving the graph.
     */
    VisitedGraphResults getVisitedGraph();

    /**
     * Returns the artifacts visited during graph resolution.
     */
    VisitedArtifactSet getVisitedArtifacts();

    /**
     * Returns true if the full graph was resolved. False if only build dependencies were resolved.
     */
    boolean isFullyResolved();

    /**
     * Results for supporting legacy resolution APIs including:
     * <ul>
     *     <li>{@link ResolvedConfiguration}</li>
     *     <li>{@link org.gradle.api.artifacts.LenientConfiguration}</li>
     *     <li>{@link org.gradle.api.artifacts.Configuration#fileCollection(Spec)} and related methods</li>
     * </ul>
     */
    interface LegacyResolverResults {

        /**
         * Returns the artifacts visited during graph resolution, filterable by the legacy selection mechanism.
         */
        LegacyVisitedArtifactSet getLegacyVisitedArtifactSet();

        /**
         * Get a legacy {@link ResolvedConfiguration}.
         *
         * @throws IllegalStateException If only build dependencies have been resolved.
         */
        ResolvedConfiguration getResolvedConfiguration();

        /**
         * Similar to {@link VisitedArtifactSet}, except artifacts can be filtered by
         * reachability from first level dependencies.
         */
        interface LegacyVisitedArtifactSet {
            /**
             * Creates a set that selects the artifacts from this set that match the given criteria.
             * Implementations are lazy, so that the selection happens only when the contents are queried.
             *
             * @param dependencySpec Select only those artifacts reachable from first level dependencies that match the given spec.
             */
            SelectedArtifactSet select(Spec<? super Dependency> dependencySpec);
        }
    }
}
