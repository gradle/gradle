/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;

/**
 * Transforms a variant artifact set by applying a set of artifact transform
 * steps to a source variant, producing a set of transformed artifacts.
 */
public class ResolvedVariantTransformer {

    private final TransformedVariantFactory transformedVariantFactory;
    private final TransformUpstreamDependenciesResolver dependenciesResolver;

    public ResolvedVariantTransformer(
        TransformedVariantFactory transformedVariantFactory,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        this.transformedVariantFactory = transformedVariantFactory;
        this.dependenciesResolver = dependenciesResolver;
    }

    public ResolvedArtifactSet transform(
        ComponentIdentifier componentId,
        ResolvedVariant sourceVariant,
        VariantDefinition variantDefinition
    ) {
        if (componentId instanceof ProjectComponentIdentifier) {
            return transformedVariantFactory.transformedProjectArtifacts(componentId, sourceVariant, variantDefinition, dependenciesResolver);
        } else {
            return transformedVariantFactory.transformedExternalArtifacts(componentId, sourceVariant, variantDefinition, dependenciesResolver);
        }
    }
}
