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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.ResolvedVariantTransformer;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

/**
 * Services provided by the consumer to be used during artifact selection.
 */
public class ArtifactSelectionServices {

    private final ArtifactVariantSelector variantSelector;
    private final ResolvedVariantTransformer resolvedVariantTransformer;
    private final VariantArtifactResolver variantResolver;
    private final GraphVariantSelector graphVariantSelector;
    private final ImmutableAttributesSchema consumerSchema;
    private final VariantTransformRegistry transformRegistry;

    public ArtifactSelectionServices(
        ArtifactVariantSelector variantSelector,
        TransformedVariantFactory transformedVariantFactory,
        TransformUpstreamDependenciesResolver dependenciesResolver,
        VariantArtifactResolver variantResolver,
        GraphVariantSelector graphVariantSelector,
        ImmutableAttributesSchema consumerSchema,
        VariantTransformRegistry transformRegistry
    ) {
        this.variantSelector = variantSelector;
        this.resolvedVariantTransformer = new ResolvedVariantTransformer(transformedVariantFactory, dependenciesResolver);
        this.variantResolver = variantResolver;
        this.graphVariantSelector = graphVariantSelector;
        this.consumerSchema = consumerSchema;
        this.transformRegistry = transformRegistry;
    }

    public ArtifactVariantSelector getArtifactVariantSelector() {
        return variantSelector;
    }

    public ResolvedVariantTransformer getResolvedVariantTransformer() {
        return resolvedVariantTransformer;
    }

    public VariantArtifactResolver getVariantArtifactResolver() {
        return variantResolver;
    }

    public GraphVariantSelector getGraphVariantSelector() {
        return graphVariantSelector;
    }

    public ImmutableAttributesSchema getConsumerSchema() {
        return consumerSchema;
    }

    public VariantTransformRegistry getTransformRegistry() {
        return transformRegistry;
    }
}
