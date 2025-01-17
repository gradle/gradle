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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

public class ArtifactTypeRegistry {

    private final Instantiator instantiator;
    private final AttributesFactory attributesFactory;
    private final CollectionCallbackActionDecorator callbackActionDecorator;
    private final AttributeContainerInternal defaultArtifactAttributes;
    private ArtifactTypeContainer artifactTypeDefinitions;

    @Inject
    public ArtifactTypeRegistry(Instantiator instantiator, AttributesFactory attributesFactory, CollectionCallbackActionDecorator callbackActionDecorator) {
        this.instantiator = instantiator;
        this.attributesFactory = attributesFactory;
        this.callbackActionDecorator = callbackActionDecorator;
        this.defaultArtifactAttributes = attributesFactory.mutable();
    }

    /**
     * Default attributes added to all artifact variants during artifact selection.
     */
    public AttributeContainerInternal getDefaultArtifactAttributes() {
        return defaultArtifactAttributes;
    }

    public ArtifactTypeContainer getArtifactTypeContainer() {
        if (artifactTypeDefinitions == null) {
            artifactTypeDefinitions = instantiator.newInstance(DefaultArtifactTypeContainer.class, instantiator, attributesFactory, callbackActionDecorator);
        }
        return artifactTypeDefinitions;
    }

    public ImmutableMap<String, ImmutableAttributes> getArtifactTypeMappings() {
        if (artifactTypeDefinitions == null) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<String, ImmutableAttributes> builder = ImmutableMap.builder();
        for (ArtifactTypeDefinition artifactTypeDefinition : artifactTypeDefinitions) {
            ImmutableAttributes attributes = ((AttributeContainerInternal) artifactTypeDefinition.getAttributes()).asImmutable();
            builder.put(artifactTypeDefinition.getName(), attributes);
        }

        return builder.build();
    }
}
