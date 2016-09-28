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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION;

public class DefaultMutableIvyModuleResolveMetadata extends AbstractMutableModuleComponentResolveMetadata implements MutableIvyModuleResolveMetadata {
    public DefaultMutableIvyModuleResolveMetadata(ModuleComponentIdentifier componentIdentifier, Set<IvyArtifactName> artifacts) {
        this(componentIdentifier,
            MutableModuleDescriptorState.createModuleDescriptor(componentIdentifier, artifacts),
            ImmutableList.of(new Configuration(DEFAULT_CONFIGURATION, true, true, ImmutableSet.<String>of())),
            ImmutableList.<DependencyMetadata>of());
    }

    public DefaultMutableIvyModuleResolveMetadata(ModuleComponentIdentifier componentIdentifier, ModuleDescriptorState descriptor, Collection<Configuration> configurations, Collection<? extends DependencyMetadata> dependencies) {
        super(componentIdentifier, descriptor, toMap(configurations), ImmutableList.copyOf(dependencies));
    }

    private static Map<String, Configuration> toMap(Collection<Configuration> configurations) {
        ImmutableMap.Builder<String, Configuration> builder = ImmutableMap.builder();
        for (Configuration configuration : configurations) {
            builder.put(configuration.getName(), configuration);
        }
        return builder.build();
    }

    public DefaultMutableIvyModuleResolveMetadata(ModuleComponentResolveMetadata metadata) {
        super(metadata);
    }

    @Override
    public IvyModuleResolveMetadata asImmutable() {
        return new DefaultIvyModuleResolveMetadata(this);
    }
}
