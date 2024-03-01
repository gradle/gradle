/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;

import javax.annotation.Nullable;

/**
 * Default implementation of {@link ResolverResults}.
 */
public class DefaultResolverResults implements ResolverResults {

    private final VisitedGraphResults graphResults;
    private final VisitedArtifactSet visitedArtifacts;
    private final LegacyResolverResults legacyResolverResults;
    private final boolean fullyResolved;

    public DefaultResolverResults(
        VisitedGraphResults graphResults,
        VisitedArtifactSet visitedArtifacts,
        LegacyResolverResults legacyResolverResults,
        boolean fullyResolved
    ) {
        this.graphResults = graphResults;
        this.visitedArtifacts = visitedArtifacts;
        this.legacyResolverResults = legacyResolverResults;
        this.fullyResolved = fullyResolved;
    }

    @Override
    public LegacyResolverResults getLegacyResults() {
        return legacyResolverResults;
    }

    @Override
    public VisitedGraphResults getVisitedGraph() {
        return graphResults;
    }

    @Override
    public VisitedArtifactSet getVisitedArtifacts() {
        return visitedArtifacts;
    }

    @Override
    public boolean isFullyResolved() {
        return fullyResolved;
    }

    /**
     * Create a new result representing the result of resolving build dependencies.
     */
    public static ResolverResults buildDependenciesResolved(
        VisitedGraphResults graphResults,
        VisitedArtifactSet visitedArtifacts,
        LegacyResolverResults legacyResolverResults
    ) {
        return new DefaultResolverResults(
            graphResults,
            visitedArtifacts,
            legacyResolverResults,
            false
        );
    }

    /**
     * Create a new result representing the result of resolving the dependency graph.
     */
    public static ResolverResults graphResolved(
        VisitedGraphResults graphResults,
        VisitedArtifactSet visitedArtifacts,
        LegacyResolverResults legacyResolverResults
    ) {
        return new DefaultResolverResults(
            graphResults,
            visitedArtifacts,
            legacyResolverResults,
            true
        );
    }

    /**
     * Default implementation of {@link LegacyResolverResults}.
     */
    public static class DefaultLegacyResolverResults implements LegacyResolverResults {
        private final LegacyVisitedArtifactSet artifacts;
        private final ResolvedConfiguration configuration;

        private DefaultLegacyResolverResults(LegacyVisitedArtifactSet artifacts, @Nullable ResolvedConfiguration configuration) {
            this.artifacts = artifacts;
            this.configuration = configuration;
        }

        @Override
        public LegacyVisitedArtifactSet getLegacyVisitedArtifactSet() {
            return artifacts;
        }

        @Override
        public ResolvedConfiguration getResolvedConfiguration() {
            if (configuration == null) {
                throw new IllegalStateException("Cannot get resolved configuration when only build dependencies are resolved.");
            }

            return configuration;
        }

        /**
         * Create a new legacy result representing the result of resolving build dependencies.
         */
        public static LegacyResolverResults buildDependenciesResolved(LegacyVisitedArtifactSet artifacts) {
            return new DefaultLegacyResolverResults(artifacts, null);
        }

        /**
         * Create a new legacy result representing the result of resolving the dependency graph.
         */
        public static LegacyResolverResults graphResolved(LegacyVisitedArtifactSet artifacts, ResolvedConfiguration configuration) {
            return new DefaultLegacyResolverResults(artifacts, configuration);
        }
    }
}
