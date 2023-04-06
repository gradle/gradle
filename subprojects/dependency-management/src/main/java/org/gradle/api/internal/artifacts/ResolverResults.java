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

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;

import javax.annotation.Nullable;
import java.util.function.Function;

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
     * Returns true if there was a failure attached to this result.
     */
    boolean hasError();

    /**
     * Returns the old model, slowly being replaced by the new model represented by {@link ResolutionResult}. Requires artifacts to be resolved.
     */
    ResolvedConfiguration getResolvedConfiguration();

    /**
     * Returns details of the artifacts visited during dependency graph resolution. This set is later refined during artifact resolution.
     */
    VisitedArtifactSet getVisitedArtifacts();

    /**
     * Returns the dependency graph resolve result.
     */
    @Nullable
    ResolutionResult getResolutionResult();

    /**
     * Returns details of the local components in the resolved dependency graph.
     */
    @Nullable
    ResolvedLocalComponentsResult getResolvedLocalComponents();

    /**
     * Returns intermediate state saved between dependency graph resolution and artifact resolution.
     */
    @Nullable
    ArtifactResolveState getArtifactResolveState();

    /**
     * Returns a new result with any non-fatal failure omitted.
     * If this result has no non-fatal failure, this result is returned without modification.
     */
    ResolverResults withoutNonFatalFailure();

    /**
     * Returns the non-fatal failure, if present.
     */
    @Nullable
    Throwable getNonFatalFailure();

    /**
     * Returns the failure, fatal or non-fatal, or null if there's no failure. Used internally to
     * set the failure on the resolution build operation result.
     */
    @Nullable
    ResolveException getFailure();

    /**
     * Return a new result with the provided {@code resolveException} attached.
     */
    ResolverResults withFailure(ResolveException failure);

    /**
     * Returns a new result with a resolution result equal to the value returned by the provided updater.
     *
     * @param updater a function that takes the current resolution result and returns a new resolution result
     */
    ResolverResults updateResolutionResult(Function<ResolutionResult, ResolutionResult> updater);
}
