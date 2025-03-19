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

package org.gradle.api.internal.attributes.immutable.artifact;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentArtifactMetadata;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE;

/**
 * Immutable counterpart to {@link ArtifactTypeRegistry}. Instances should
 * be only created with {@link ImmutableArtifactTypeRegistryFactory}.
 * <p>
 * This class is deeply immutable and thread safe. Instances created with
 * {@link ImmutableArtifactTypeRegistryFactory} are interned and therefore
 * can be compared with reference equality.
 */
public class ImmutableArtifactTypeRegistry {

    private final AttributesFactory attributesFactory;
    private final ImmutableMap<String, ImmutableAttributes> mappings;
    private final ImmutableAttributes defaultArtifactAttributes;

    private final int hashCode;

    public ImmutableArtifactTypeRegistry(
        AttributesFactory attributesFactory,
        ImmutableMap<String, ImmutableAttributes> mappings,
        ImmutableAttributes defaultArtifactAttributes
    ) {
        this.attributesFactory = attributesFactory;
        this.mappings = mappings;
        this.defaultArtifactAttributes = defaultArtifactAttributes;

        this.hashCode = computeHashCode(mappings, defaultArtifactAttributes);
    }

    public ImmutableMap<String, ImmutableAttributes> getMappings() {
        return mappings;
    }

    public ImmutableAttributes getDefaultArtifactAttributes() {
        return defaultArtifactAttributes;
    }

    public void visitArtifactTypeAttributes(Collection<TransformRegistration> transformRegistrations, Consumer<? super ImmutableAttributes> action) {
        // Apply default attributes before visiting
        Consumer<? super ImmutableAttributes> visitor = attributes -> {
            ImmutableAttributes attributesPlusDefaults = attributesFactory.concat(defaultArtifactAttributes.asImmutable(), attributes);
            action.accept(attributesPlusDefaults);
        };

        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, ImmutableAttributes> artifactTypeDefinition : mappings.entrySet()) {
            if (seen.add(artifactTypeDefinition.getKey())) {
                ImmutableAttributes attributes = artifactTypeDefinition.getValue();
                attributes = attributesFactory.concat(attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, artifactTypeDefinition.getKey()), attributes);
                visitor.accept(attributes);
            }
        }

        for (TransformRegistration registration : transformRegistrations) {
            AttributeContainerInternal sourceAttributes = registration.getFrom();
            String format = sourceAttributes.getAttribute(ARTIFACT_TYPE_ATTRIBUTE);
            if (format != null && seen.add(format)) {
                // Some artifact type that has not already been visited
                ImmutableAttributes attributes = attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, format);
                visitor.accept(attributes);
            }
        }

        if (seen.add(ArtifactTypeDefinition.DIRECTORY_TYPE)) {
            ImmutableAttributes directory = attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE);
            visitor.accept(directory);
        }
    }

    public ImmutableAttributes mapAttributesFor(File file) {
        ImmutableAttributes withoutDefaultAttributes = mapWithoutDefaultAttributesFor(file);
        return attributesFactory.concat(defaultArtifactAttributes.asImmutable(), withoutDefaultAttributes);
    }

    private ImmutableAttributes mapWithoutDefaultAttributesFor(File file) {
        if (file.isDirectory()) {
            return attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE);
        } else {
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            String extension = Files.getFileExtension(file.getName());
            attributes = applyForExtension(attributes, extension);
            return attributesFactory.concat(attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, extension), attributes);
        }
    }

    public ImmutableAttributes mapAttributesFor(ImmutableAttributes attributes, Iterable<? extends ComponentArtifactMetadata> artifacts) {
        ImmutableAttributes withoutDefaultAttributes = mapWithoutDefaultAttributesFor(attributes, artifacts);
        return attributesFactory.concat(defaultArtifactAttributes.asImmutable(), withoutDefaultAttributes);
    }

    private ImmutableAttributes mapWithoutDefaultAttributesFor(ImmutableAttributes attributes, Iterable<? extends ComponentArtifactMetadata> artifacts) {
        // Add attributes to be applied given the extension
        if (!mappings.isEmpty()) {
            String extension = null;
            for (ComponentArtifactMetadata artifact : artifacts) {
                String candidateExtension = artifact.getName().getExtension();
                if (extension == null) {
                    extension = candidateExtension;
                } else if (!extension.equals(candidateExtension)) {
                    extension = null;
                    break;
                }
            }
            if (extension != null) {
                attributes = applyForExtension(attributes, extension);
            }
        }

        // Add artifact format as an implicit attribute when all artifacts have the same format
        if (!attributes.contains(ARTIFACT_TYPE_ATTRIBUTE)) {
            String format = null;
            for (ComponentArtifactMetadata artifact : artifacts) {
                String candidateFormat = artifact.getName().getType();
                if (format == null) {
                    format = candidateFormat;
                } else if (!format.equals(candidateFormat)) {
                    format = null;
                    break;
                }
            }
            if (format != null) {
                attributes = attributesFactory.concat(attributes.asImmutable(), ARTIFACT_TYPE_ATTRIBUTE, format);
            }
        }

        return attributes;
    }

    private ImmutableAttributes applyForExtension(ImmutableAttributes attributes, String extension) {
        ImmutableAttributes definition = mappings.get(extension);
        if (definition != null) {
            attributes = attributesFactory.concat(definition, attributes);
        }
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableArtifactTypeRegistry that = (ImmutableArtifactTypeRegistry) o;
        return mappings.equals(that.mappings) &&
            defaultArtifactAttributes.equals(that.defaultArtifactAttributes);
    }

    private static int computeHashCode(
        ImmutableMap<String, ImmutableAttributes> mappings,
        ImmutableAttributes defaultArtifactAttributes
    ) {
        int result = mappings.hashCode();
        result = 31 * result + defaultArtifactAttributes.hashCode();
        return result;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
