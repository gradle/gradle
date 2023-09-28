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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;

import javax.annotation.Nullable;

/**
 * Default implementation of {@link ResolverResults} containing the results of a complete resolution.
 */
public class DefaultResolverResults implements ResolverResults {

    private final ResolvedLocalComponentsResult resolvedLocalComponentsResult;
    private final VisitedGraphResults graphResults;
    private final VisitedArtifactSet visitedArtifacts;
    private final ArtifactResolveState artifactResolveState;
    private final ResolvedConfiguration resolvedConfiguration;

    public DefaultResolverResults(
        ResolvedLocalComponentsResult resolvedLocalComponentsResult,
        VisitedGraphResults graphResults,
        VisitedArtifactSet visitedArtifacts,
        @Nullable ArtifactResolveState artifactResolveState,
        @Nullable ResolvedConfiguration resolvedConfiguration
    ) {
        this.resolvedLocalComponentsResult = resolvedLocalComponentsResult;
        this.graphResults = graphResults;
        this.visitedArtifacts = visitedArtifacts;
        this.artifactResolveState = artifactResolveState;
        this.resolvedConfiguration = resolvedConfiguration;
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        if (resolvedConfiguration == null) {
            throw new IllegalStateException("Cannot get ResolvedConfiguration before graph resolution.");
        }
        return resolvedConfiguration;
    }

    @Override
    public VisitedGraphResults getVisitedGraph() {
        return graphResults;
    }

    @Override
    public ResolvedLocalComponentsResult getResolvedLocalComponents() {
        return resolvedLocalComponentsResult;
    }

    @Override
    public ArtifactResolveState getArtifactResolveState() {
        return artifactResolveState;
    }

    @Override
    public VisitedArtifactSet getVisitedArtifacts() {
        return visitedArtifacts;
    }

    /**
     * Create a new result representing the result of resolving build dependencies.
     */
    public static ResolverResults buildDependenciesResolved(
        VisitedGraphResults graphResults,
        ResolvedLocalComponentsResult resolvedLocalComponentsResult,
        VisitedArtifactSet visitedArtifacts
    ) {
        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            graphResults,
            visitedArtifacts,
            null,
            null
        );
    }

    /**
     * Create a new result representing the result of resolving the dependency graph.
     */
    public static ResolverResults graphResolved(
        VisitedGraphResults graphResults,
        ResolvedLocalComponentsResult resolvedLocalComponentsResult,
        VisitedArtifactSet visitedArtifacts,
        @Nullable ArtifactResolveState artifactResolveState
    ) {
        return new DefaultResolverResults(
            resolvedLocalComponentsResult,
            graphResults,
            visitedArtifacts,
            artifactResolveState,
            null
        );
    }

    /**
     * Create a new result representing the result of resolving the artifacts.
     */
    public static ResolverResults artifactsResolved(VisitedGraphResults graphResults, ResolvedLocalComponentsResult localComponentsResult, ResolvedConfiguration resolvedConfiguration, VisitedArtifactSet visitedArtifacts) {
        return new DefaultResolverResults(
            localComponentsResult,
            graphResults,
            visitedArtifacts,
            null, // Do not need to keep the artifact resolve state around after artifact resolution
            resolvedConfiguration
        );
    }
}
