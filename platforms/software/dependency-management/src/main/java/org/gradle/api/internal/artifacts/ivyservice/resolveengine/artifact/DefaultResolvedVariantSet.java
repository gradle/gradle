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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.ResolvedVariantTransformer;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;

/**
 * Default implementation of {@link ResolvedVariantSet}.
 */
public class DefaultResolvedVariantSet implements ResolvedVariantSet {
    private final ComponentIdentifier componentIdentifier;
    private final ImmutableAttributesSchema schema;
    private final ImmutableAttributes selectionAttributes;
    private final ImmutableList<ResolvedVariant> variants;

    // Services
    private final ResolvedVariantTransformer transformer;

    public DefaultResolvedVariantSet(
        ComponentIdentifier componentIdentifier,
        ImmutableAttributesSchema schema,
        ImmutableAttributes selectionAttributes,
        ImmutableList<ResolvedVariant> variants,
        ResolvedVariantTransformer transformer
    ) {
        this.componentIdentifier = componentIdentifier;
        this.schema = schema;
        this.selectionAttributes = selectionAttributes;
        this.variants = variants;

        this.transformer = transformer;
    }

    @Override
    public ComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public ImmutableAttributes getOverriddenAttributes() {
        return selectionAttributes;
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public Describable asDescribable() {
        return Describables.of(componentIdentifier);
    }

    @Override
    public ImmutableAttributesSchema getProducerSchema() {
        return schema;
    }

    @Override
    public ImmutableList<ResolvedVariant> getCandidates() {
        return variants;
    }

    @Override
    public ResolvedArtifactSet transformCandidate(
        ResolvedVariant candidate,
        VariantDefinition variantDefinition
    ) {
        return transformer.transform(componentIdentifier, candidate, variantDefinition);
    }
}
