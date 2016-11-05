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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResult;

public interface ResolverResults {
    boolean hasError();

    /**
     * Returns the old model, slowly being replaced by the new model represented by {@link ResolutionResult}. Requires artifacts to be resolved.
     */
    ResolvedConfiguration getResolvedConfiguration();

    /**
     * Returns details of the resolved artifacts.
     */
    ArtifactResults getArtifactResults();

    /**
     * Returns the dependency graph resolve result.
     */
    ResolutionResult getResolutionResult();

    /**
     * Returns details of the local components in the resolved dependency graph.
     */
    ResolvedLocalComponentsResult getResolvedLocalComponents();

    /**
     * Returns details of the file dependencies in the resolved dependency graph.
     */
    FileDependencyResults getFileDependencies();

    /**
     * Marks the dependency graph resolution as successful, with the given result.
     */
    void graphResolved(ResolvedLocalComponentsResult resolvedLocalComponentsResult, FileDependencyResults fileDependencyResults);

    /**
     * Marks the dependency graph resolution as successful, with the given result.
     */
    void graphResolved(ResolutionResult resolutionResult, ResolvedLocalComponentsResult resolvedLocalComponentsResult, FileDependencyResults fileDependencyResults);

    void failed(ResolveException failure);

    /**
     * Attaches some opaque state calculated during dependency graph resolution that will later be required to resolve the artifacts.
     */
    void retainState(Object artifactResolveState);

    /**
     * Returns the opaque state required to resolve the artifacts.
     */
    Object getArtifactResolveState();

    /**
     * Marks artifact resolution as successful, clearing state provided by {@link #retainState(Object)}.
     */
    void artifactsResolved(ResolvedConfiguration resolvedConfiguration, ArtifactResults artifactResults);
}
