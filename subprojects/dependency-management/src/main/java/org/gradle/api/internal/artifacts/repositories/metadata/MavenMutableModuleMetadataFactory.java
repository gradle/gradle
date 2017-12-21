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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.model.DefaultMutableMavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.MavenDependencyDescriptor;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;

import java.util.Collections;
import java.util.List;

public class MavenMutableModuleMetadataFactory implements MutableModuleMetadataFactory<MutableMavenModuleResolveMetadata> {
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator objectInstantiator;
    private final ExperimentalFeatures experimentalFeatures;

    public MavenMutableModuleMetadataFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                             ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator objectInstantiator,
                                             ExperimentalFeatures experimentalFeatures) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.attributesFactory = attributesFactory;
        this.objectInstantiator = objectInstantiator;
        this.experimentalFeatures = experimentalFeatures;
    }

    @Override
    public MutableMavenModuleResolveMetadata create(ModuleComponentIdentifier from) {
        ModuleVersionIdentifier mvi = asVersionIdentifier(from);
        return new DefaultMutableMavenModuleResolveMetadata(mvi, from, Collections.<MavenDependencyDescriptor>emptyList(), attributesFactory, objectInstantiator, experimentalFeatures);
    }

    private ModuleVersionIdentifier asVersionIdentifier(ModuleComponentIdentifier from) {
        return moduleIdentifierFactory.moduleWithVersion(from.getGroup(), from.getModule(), from.getVersion());
    }

    @Override
    public MutableMavenModuleResolveMetadata missing(ModuleComponentIdentifier from) {
        MutableMavenModuleResolveMetadata metadata = create(from);
        metadata.setMissing(true);
        return MavenResolver.processMetaData(metadata);
    }

    public MutableMavenModuleResolveMetadata create(ModuleComponentIdentifier from, List<MavenDependencyDescriptor> dependencies) {
        ModuleVersionIdentifier mvi = asVersionIdentifier(from);
        return new DefaultMutableMavenModuleResolveMetadata(mvi, from, dependencies, attributesFactory, objectInstantiator, experimentalFeatures);
    }
}
