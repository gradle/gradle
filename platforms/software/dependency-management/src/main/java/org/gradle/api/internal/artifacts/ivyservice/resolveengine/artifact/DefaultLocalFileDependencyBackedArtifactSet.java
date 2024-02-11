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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;

/**
 * Default implementation of {@link LocalFileDependencyBackedArtifactSet}.
 */
public class DefaultLocalFileDependencyBackedArtifactSet extends LocalFileDependencyBackedArtifactSet {

    private final ImmutableAttributes requestAttributes;

    public DefaultLocalFileDependencyBackedArtifactSet(
        LocalFileDependencyMetadata dependencyMetadata,
        Spec<? super ComponentIdentifier> componentFilter,
        ArtifactVariantSelector variantSelector,
        ArtifactTypeRegistry artifactTypeRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        ImmutableAttributes requestAttributes,
        boolean allowNoMatchingVariants
    ) {
        super(
            dependencyMetadata,
            componentFilter,
            variantSelector,
            artifactTypeRegistry,
            calculatedValueContainerFactory,
            allowNoMatchingVariants
        );
        this.requestAttributes = requestAttributes;
    }

    @Override
    public ImmutableAttributes getRequestAttributes() {
        return requestAttributes;
    }
}
