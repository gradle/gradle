/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;

/**
 * Selects artifacts from all visited artifacts in a graph.
 */
public class DefaultVisitedArtifactSet implements VisitedArtifactSet {
    private final VisitedGraphResults graphResults;
    private final ResolutionHost resolutionHost;
    private final VisitedArtifactResults artifactsResults;
    private final ResolvedArtifactSetResolver artifactSetResolver;
    private final ArtifactVariantSelector artifactVariantSelector;

    public DefaultVisitedArtifactSet(
        VisitedGraphResults graphResults,
        ResolutionHost resolutionHost,
        VisitedArtifactResults artifactsResults,
        ResolvedArtifactSetResolver artifactSetResolver,
        ArtifactVariantSelector artifactVariantSelector
    ) {
        this.graphResults = graphResults;
        this.resolutionHost = resolutionHost;
        this.artifactsResults = artifactsResults;
        this.artifactSetResolver = artifactSetResolver;
        this.artifactVariantSelector = artifactVariantSelector;
    }

    @Override
    public SelectedArtifactSet select(ArtifactSelectionSpec spec) {
        SelectedArtifactResults artifacts = artifactsResults.select(artifactVariantSelector, spec, false);
        return new DefaultSelectedArtifactSet(artifactSetResolver, graphResults, artifacts.getArtifacts(), resolutionHost);
    }
}
