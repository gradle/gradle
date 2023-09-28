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

import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;

import javax.annotation.Nullable;

/**
 * Immutable representation of the state of dependency resolution. Can represent intermediate resolution states after
 * build dependency resolution, graph resolution, and artifact resolution. Results can have attached failures
 * in cases of partial resolution successes.
 *
 * <p>This should eventually be merged with {@link org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.ResolveState}</p>
 */
@SuppressWarnings("JavadocReference")
public interface ResolverResults {

    /**
     * Returns the old model, which has been replaced by {@link VisitedGraphResults} and {@link VisitedArtifactSet}.
     * Using this model directly should be avoided.
     * This method should only be used to implement existing public API methods.
     */
    ResolvedConfiguration getResolvedConfiguration();

    /**
     * Return the model representing the resolved graph. This model provides access
     * to the root component as well as any failure that occurred while resolving the graph.
     */
    VisitedGraphResults getVisitedGraph();

    /**
     * Returns details of the artifacts visited during dependency graph resolution. This set is later refined during artifact resolution.
     */
    VisitedArtifactSet getVisitedArtifacts();

    /**
     * Returns details of the local components in the resolved dependency graph.
     */
    ResolvedLocalComponentsResult getResolvedLocalComponents();

    /**
     * Returns intermediate state saved between dependency graph resolution and artifact resolution.
     */
    @Nullable
    ArtifactResolveState getArtifactResolveState();
}
