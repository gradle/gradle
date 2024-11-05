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
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.DefaultVariantArtifactResolver;

/**
 * Selects artifacts from all visited artifacts in a graph.
 */
public class DefaultVisitedArtifactSet implements VisitedArtifactSet {
    private final VisitedGraphResults graphResults;
    private final ResolutionHost resolutionHost;
    private final VisitedArtifactResults artifactsResults;
    private final ResolvedArtifactSetResolver artifactSetResolver;

    private final ArtifactSelectionServices consumerServices;

    public DefaultVisitedArtifactSet(
        VisitedGraphResults graphResults,
        ResolutionHost resolutionHost,
        VisitedArtifactResults artifactsResults,
        ResolvedArtifactSetResolver artifactSetResolver,
        TransformedVariantFactory transformedVariantFactory,
        TransformUpstreamDependenciesResolver dependenciesResolver,
        ImmutableAttributesSchema consumerSchema,
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler resolutionFailureHandler,
        ArtifactResolver artifactResolver,
        ArtifactTypeRegistry artifactTypeRegistry,
        ResolvedVariantCache resolvedVariantCache,
        GraphVariantSelector graphVariantSelector
    ) {
        this.graphResults = graphResults;
        this.resolutionHost = resolutionHost;
        this.artifactsResults = artifactsResults;
        this.artifactSetResolver = artifactSetResolver;

        ArtifactVariantSelector artifactVariantSelector = new AttributeMatchingArtifactVariantSelector(
            consumerSchema,
            consumerProvidedVariantFinder,
            attributesFactory,
            attributeSchemaServices,
            resolutionFailureHandler
        );

        this.consumerServices = new ArtifactSelectionServices(
            artifactVariantSelector,
            transformedVariantFactory,
            dependenciesResolver,
            new DefaultVariantArtifactResolver(artifactResolver, artifactTypeRegistry, resolvedVariantCache),
            graphVariantSelector,
            consumerSchema
        );
    }

    @Override
    public SelectedArtifactSet select(ArtifactSelectionSpec spec) {
        SelectedArtifactResults artifacts = artifactsResults.select(consumerServices, spec, false);
        return new DefaultSelectedArtifactSet(artifactSetResolver, graphResults, artifacts.getArtifacts(), resolutionHost);
    }

    @Override
    public SelectedArtifactResults selectLegacy(ArtifactSelectionSpec spec, boolean lenient) {
        return artifactsResults.select(consumerServices, spec, lenient);
    }
}
