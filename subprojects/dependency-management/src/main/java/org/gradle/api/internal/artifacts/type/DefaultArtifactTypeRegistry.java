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

package org.gradle.api.internal.artifacts.type;

import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.VariantMetadata;
import org.gradle.internal.reflect.Instantiator;

public class DefaultArtifactTypeRegistry implements ArtifactTypeRegistry {
    private final Instantiator instantiator;
    private final ImmutableAttributesFactory attributesFactory;
    private ArtifactTypeContainer artifactTypeDefinitions;

    public DefaultArtifactTypeRegistry(Instantiator instantiator, ImmutableAttributesFactory attributesFactory) {
        this.instantiator = instantiator;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public ArtifactTypeContainer create() {
        if (artifactTypeDefinitions == null) {
            artifactTypeDefinitions = instantiator.newInstance(DefaultArtifactTypeContainer.class, instantiator, attributesFactory);
        }
        return artifactTypeDefinitions;
    }

    @Override
    public ImmutableAttributes mapAttributesFor(VariantMetadata variant) {
        // Add artifact format as an implicit attribute when all artifacts have the same format
        AttributeContainerInternal attributes = variant.getAttributes();
        if (artifactTypeDefinitions != null) {
            String extension = null;
            for (ComponentArtifactMetadata artifact : variant.getArtifacts()) {
                String candidateExtension = artifact.getName().getExtension();
                if (extension == null) {
                    extension = candidateExtension;
                } else if (!extension.equals(candidateExtension)) {
                    extension = null;
                    break;
                }
            }
            if (extension != null) {
                ArtifactTypeDefinition definition = artifactTypeDefinitions.findByName(extension);
                if (definition != null) {
                    attributes = attributesFactory.concat(((AttributeContainerInternal) definition.getAttributes()).asImmutable(), attributes.asImmutable());
                }
            }
        }
        if (!attributes.contains(ArtifactAttributes.ARTIFACT_FORMAT)) {
            String format = null;
            for (ComponentArtifactMetadata artifact : variant.getArtifacts()) {
                String candidateFormat = artifact.getName().getType();
                if (format == null) {
                    format = candidateFormat;
                } else if (!format.equals(candidateFormat)) {
                    format = null;
                    break;
                }
            }
            if (format != null) {
                attributes = attributesFactory.concat(attributes.asImmutable(), ArtifactAttributes.ARTIFACT_FORMAT, format);
            }
        }
        return attributes.asImmutable();
    }
}
