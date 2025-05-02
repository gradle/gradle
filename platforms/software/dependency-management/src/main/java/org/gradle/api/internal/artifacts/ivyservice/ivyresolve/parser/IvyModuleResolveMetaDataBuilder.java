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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor;
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class IvyModuleResolveMetaDataBuilder {
    private final List<Artifact> artifacts = new ArrayList<>();
    private final DefaultModuleDescriptor ivyDescriptor;
    private final IvyModuleDescriptorConverter converter;
    private final IvyMutableModuleMetadataFactory metadataFactory;

    public IvyModuleResolveMetaDataBuilder(DefaultModuleDescriptor module, IvyModuleDescriptorConverter converter, IvyMutableModuleMetadataFactory metadataFactory) {
        this.ivyDescriptor = module;
        this.converter = converter;
        this.metadataFactory = metadataFactory;
    }

    public void addArtifact(IvyArtifactName newArtifact, Set<String> configurations) {
        if (configurations.isEmpty()) {
            throw new IllegalArgumentException("Artifact should be attached to at least one configuration.");
        }
        Artifact artifact = findOrCreate(newArtifact);
        artifact.getConfigurations().addAll(configurations);
    }

    private Artifact findOrCreate(IvyArtifactName artifactName) {
        for (Artifact existingArtifact : artifacts) {
            if (existingArtifact.getArtifactName().equals(artifactName)) {
                return existingArtifact;
            }
        }
        Artifact newArtifact = new Artifact(artifactName);
        artifacts.add(newArtifact);
        return newArtifact;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public MutableIvyModuleResolveMetadata build() {
        ModuleRevisionId moduleRevisionId = ivyDescriptor.getModuleRevisionId();
        ModuleComponentIdentifier cid = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName()), moduleRevisionId.getRevision());
        List<Configuration> configurations = converter.extractConfigurations(ivyDescriptor);
        List<IvyDependencyDescriptor> dependencies = converter.extractDependencies(ivyDescriptor);
        List<Exclude> excludes = converter.extractExcludes(ivyDescriptor);
        Map<NamespaceId, String> extraAttributes = converter.extractExtraAttributes(ivyDescriptor);
        MutableIvyModuleResolveMetadata metadata = metadataFactory.create(cid, dependencies, configurations, artifacts, excludes);
        metadata.setStatus(ivyDescriptor.getStatus());
        metadata.setExtraAttributes(extraAttributes);
        metadata.setBranch(ivyDescriptor.getModuleRevisionId().getBranch());
        return metadata;
    }
}
