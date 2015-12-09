/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.model.*;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetadata extends AbstractModuleDescriptorBackedMetadata implements MutableModuleComponentResolveMetadata {
    private Multimap<String, ModuleComponentArtifactMetadata> artifactsByConfig;

    public AbstractModuleComponentResolveMetadata(ModuleDescriptor moduleDescriptor) {
        this(moduleVersionIdentifier(moduleDescriptor), moduleDescriptor, moduleComponentIdentifier(moduleDescriptor));
    }

    private static ModuleVersionIdentifier moduleVersionIdentifier(ModuleDescriptor descriptor) {
        return DefaultModuleVersionIdentifier.newId(descriptor.getModuleRevisionId());
    }

    private static ModuleComponentIdentifier moduleComponentIdentifier(ModuleDescriptor descriptor) {
        return DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier(descriptor));
    }

    public AbstractModuleComponentResolveMetadata(ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentIdentifier) {
        super(moduleVersionIdentifier, moduleDescriptor, componentIdentifier);
    }

    protected void copyTo(AbstractModuleComponentResolveMetadata copy) {
        super.copyTo(copy);
        copy.artifactsByConfig = artifactsByConfig;
    }

    public abstract AbstractModuleComponentResolveMetadata copy();

    public MutableModuleComponentResolveMetadata withSource(ModuleSource source) {
        AbstractModuleComponentResolveMetadata copy = copy();
        copy.setModuleSource(source);
        return copy;
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return (ModuleComponentIdentifier) super.getComponentId();
    }

    @Override
    public void setComponentId(ModuleComponentIdentifier componentId) {
        super.setComponentId(componentId);
        setId(DefaultModuleVersionIdentifier.newId(componentId));
    }

    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getComponentId(), ivyArtifactName);
    }

    public void setArtifacts(Iterable<? extends ModuleComponentArtifactMetadata> artifacts) {
        this.artifactsByConfig = LinkedHashMultimap.create();
        for (String config : getDescriptor().getConfigurationsNames()) {
            artifactsByConfig.putAll(config, artifacts);
        }
    }

    protected Set<ComponentArtifactMetadata> getArtifactsForConfiguration(ConfigurationMetadata configurationMetadata) {
        if (artifactsByConfig == null) {
            populateArtifactsFromDescriptor();
        }
        Set<ComponentArtifactMetadata> artifactMetaData = new LinkedHashSet<ComponentArtifactMetadata>();
        for (String ancestor : configurationMetadata.getHierarchy()) {
            artifactMetaData.addAll(artifactsByConfig.get(ancestor));
        }
        return artifactMetaData;
    }

    private void populateArtifactsFromDescriptor() {
        Map<Artifact, ModuleComponentArtifactMetadata> artifactToMetaData = Maps.newLinkedHashMap();
        for (Artifact descriptorArtifact : getDescriptor().getAllArtifacts()) {
            IvyArtifactName artifactName = DefaultIvyArtifactName.forIvyArtifact(descriptorArtifact);
            ModuleComponentArtifactMetadata artifact = new DefaultModuleComponentArtifactMetadata(getComponentId(), artifactName);
            artifactToMetaData.put(descriptorArtifact, artifact);
        }

        this.artifactsByConfig = LinkedHashMultimap.create();
        for (String configuration : getDescriptor().getConfigurationsNames()) {
            Artifact[] configArtifacts = getDescriptor().getArtifacts(configuration);
            for (Artifact configArtifact : configArtifacts) {
                artifactsByConfig.put(configuration, artifactToMetaData.get(configArtifact));
            }
        }
    }
}
