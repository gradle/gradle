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

import com.google.common.io.Files;
import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE;

public class DefaultArtifactTypeRegistry implements ArtifactTypeRegistry {
    private final Instantiator instantiator;
    private final ImmutableAttributesFactory attributesFactory;
    private final CollectionCallbackActionDecorator callbackActionDecorator;
    private final VariantTransformRegistry transformRegistry;
    private ArtifactTypeContainer artifactTypeDefinitions;

    public DefaultArtifactTypeRegistry(Instantiator instantiator, ImmutableAttributesFactory attributesFactory, CollectionCallbackActionDecorator callbackActionDecorator, VariantTransformRegistry transformRegistry) {
        this.instantiator = instantiator;
        this.attributesFactory = attributesFactory;
        this.callbackActionDecorator = callbackActionDecorator;
        this.transformRegistry = transformRegistry;
    }

    @Override
    public void visitArtifactTypes(Consumer<? super ImmutableAttributes> action) {
        Set<String> seen = new HashSet<>();

        if (artifactTypeDefinitions != null) {
            for (ArtifactTypeDefinition artifactTypeDefinition : artifactTypeDefinitions) {
                if (seen.add(artifactTypeDefinition.getName())) {
                    ImmutableAttributes attributes = ((AttributeContainerInternal) artifactTypeDefinition.getAttributes()).asImmutable();
                    attributes = attributesFactory.concat(attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, artifactTypeDefinition.getName()), attributes);
                    action.accept(attributes);
                }
            }
        }

        for (TransformRegistration registration : transformRegistry.getRegistrations()) {
            AttributeContainerInternal sourceAttributes = registration.getFrom();
            String format = sourceAttributes.getAttribute(ARTIFACT_TYPE_ATTRIBUTE);
            if (format != null && seen.add(format)) {
                // Some artifact type that has not already been visited
                ImmutableAttributes attributes = attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, format);
                action.accept(attributes);
            }
        }

        if (seen.add(ArtifactTypeDefinition.DIRECTORY_TYPE)) {
            ImmutableAttributes directory = attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE);
            action.accept(directory);
        }
    }

    @Override
    public ArtifactTypeContainer create() {
        if (artifactTypeDefinitions == null) {
            artifactTypeDefinitions = instantiator.newInstance(DefaultArtifactTypeContainer.class, instantiator, attributesFactory, callbackActionDecorator);
        }
        return artifactTypeDefinitions;
    }

    @Override
    public ImmutableAttributes mapAttributesFor(File file) {
        if (file.isDirectory()) {
            return attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE);
        } else {
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            String extension = Files.getFileExtension(file.getName());
            if (artifactTypeDefinitions != null) {
                attributes = applyForExtension(attributes, extension);
            }
            return attributesFactory.concat(attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, extension), attributes);
        }
    }

    @Override
    public ImmutableAttributes mapAttributesFor(ImmutableAttributes attributes, Iterable<? extends ComponentArtifactMetadata> artifacts) {
        // Add attributes to be applied given the extension
        if (artifactTypeDefinitions != null) {
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
        ArtifactTypeDefinition definition = artifactTypeDefinitions.findByName(extension);
        if (definition != null) {
            attributes = attributesFactory.concat(((AttributeContainerInternal) definition.getAttributes()).asImmutable(), attributes);
        }
        return attributes;
    }
}
