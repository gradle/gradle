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

import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.ResolvedVariantTransformer;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;

/**
 * Services provided by the consumer to be used during artifact selection.
 */
public class ArtifactSelectionServices {

    private final ArtifactVariantSelector variantSelector;
    private final ResolvedVariantTransformer resolvedVariantTransformer;

    public ArtifactSelectionServices(
        ArtifactVariantSelector variantSelector,
        TransformedVariantFactory transformedVariantFactory,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        this.variantSelector = variantSelector;
        this.resolvedVariantTransformer = new ResolvedVariantTransformer(transformedVariantFactory, dependenciesResolver);
    }

    public ArtifactVariantSelector getArtifactVariantSelector() {
        return variantSelector;
    }

    public ResolvedVariantTransformer getResolvedVariantTransformer() {
        return resolvedVariantTransformer;
    }
}
