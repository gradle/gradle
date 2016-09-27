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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import com.google.common.collect.Lists;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.external.model.DefaultMutableIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.IvyDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;
import java.util.Set;

class IvyModuleResolveMetaDataBuilder {
    private final List<Artifact> artifacts = Lists.newArrayList();
    private final DefaultModuleDescriptor ivyDescriptor;

    public IvyModuleResolveMetaDataBuilder(DefaultModuleDescriptor module) {
        this.ivyDescriptor = module;
    }

    public void addArtifact(IvyArtifactName newArtifact, Set<String> configurations) {
        artifacts.add(new Artifact(newArtifact, configurations));
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public DefaultMutableIvyModuleResolveMetadata build() {
        ModuleDescriptorState descriptorState = IvyModuleDescriptorConverter.forIvyModuleDescriptor(ivyDescriptor);
        for (Artifact artifact : artifacts) {
            descriptorState.addArtifact(artifact.getArtifactName(), artifact.getConfigurations());
        }
        List<Configuration> configurations = IvyModuleDescriptorConverter.extractConfigurations(ivyDescriptor);
        List<IvyDependencyMetadata> dependencies = IvyModuleDescriptorConverter.extractDependencies(ivyDescriptor);
        return new DefaultMutableIvyModuleResolveMetadata(descriptorState.getComponentIdentifier(), descriptorState, configurations, dependencies);
    }
}
