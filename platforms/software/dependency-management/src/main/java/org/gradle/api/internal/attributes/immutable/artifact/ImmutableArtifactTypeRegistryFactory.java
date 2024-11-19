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

package org.gradle.api.internal.attributes.immutable.artifact;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryInterner;

/**
 * Creates and interns {@link ImmutableArtifactTypeRegistry} instances
 * from {@link ArtifactTypeRegistry} instances.
 */
public class ImmutableArtifactTypeRegistryFactory {

    private final AttributesFactory attributesFactory;
    private final InMemoryInterner<ImmutableArtifactTypeRegistry> registries;

    public ImmutableArtifactTypeRegistryFactory(
        InMemoryCacheFactory cacheFactory,
        AttributesFactory attributesFactory
    ) {
        this.attributesFactory = attributesFactory;
        this.registries = cacheFactory.createInterner();
    }

    public ImmutableArtifactTypeRegistry create(ArtifactTypeRegistry registry) {
        ImmutableMap<String, ImmutableAttributes> artifactTypeMappings = registry.getArtifactTypeMappings();

        return registries.intern(new ImmutableArtifactTypeRegistry(
            attributesFactory,
            artifactTypeMappings,
            registry.getDefaultArtifactAttributes().asImmutable()
        ));
    }

}
