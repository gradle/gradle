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
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.model.AbstractModuleDescriptorBackedMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.component.model.ModuleSource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetaData extends AbstractModuleDescriptorBackedMetaData implements MutableModuleComponentResolveMetaData {
    private Set<ModuleComponentArtifactMetaData> artifacts;
    private Multimap<String, ModuleComponentArtifactMetaData> artifactsByConfig;

    public AbstractModuleComponentResolveMetaData(ModuleDescriptor moduleDescriptor) {
        this(moduleVersionIdentifier(moduleDescriptor), moduleDescriptor, moduleComponentIdentifier(moduleDescriptor));
    }
    
    private static ModuleVersionIdentifier moduleVersionIdentifier(ModuleDescriptor descriptor) {
        return DefaultModuleVersionIdentifier.newId(descriptor.getModuleRevisionId());
    }

    private static ModuleComponentIdentifier moduleComponentIdentifier(ModuleDescriptor descriptor) {
        return DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier(descriptor));
    }

    public AbstractModuleComponentResolveMetaData(ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentIdentifier) {
        super(moduleVersionIdentifier, moduleDescriptor, componentIdentifier);
    }

    protected void copyTo(AbstractModuleComponentResolveMetaData copy) {
        super.copyTo(copy);
        copy.artifacts = artifacts;
        copy.artifactsByConfig = artifactsByConfig;
    }

    public abstract AbstractModuleComponentResolveMetaData copy();

    public MutableModuleComponentResolveMetaData withSource(ModuleSource source) {
        AbstractModuleComponentResolveMetaData copy = copy();
        copy.setModuleSource(source);
        return copy;
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return (ModuleComponentIdentifier) super.getComponentId();
    }

    public ModuleComponentArtifactMetaData artifact(Artifact artifact) {
        return new DefaultModuleComponentArtifactMetaData(getComponentId(), artifact);
    }

    public ModuleComponentArtifactMetaData artifact(String type, @Nullable String extension, @Nullable String classifier) {
        Map extraAttributes = classifier == null ? Collections.emptyMap() : Collections.singletonMap("m:classifier", classifier);
        Artifact artifact = new DefaultArtifact(getDescriptor().getModuleRevisionId(), null, getId().getName(), type, extension, extraAttributes);
        return new DefaultModuleComponentArtifactMetaData(getComponentId(), artifact);
    }

    public Set<ModuleComponentArtifactMetaData> getArtifacts() {
        if (artifacts == null) {
            populateArtifactsFromDescriptor();
        }
        return artifacts;
    }

    public void setArtifacts(Iterable<? extends ModuleComponentArtifactMetaData> artifacts) {
        this.artifacts = Sets.newLinkedHashSet(artifacts);
        this.artifactsByConfig = LinkedHashMultimap.create();
        for (String config : getDescriptor().getConfigurationsNames()) {
            artifactsByConfig.putAll(config, artifacts);
        }
    }

    protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
        if (artifactsByConfig == null) {
            populateArtifactsFromDescriptor();
        }
        Set<ComponentArtifactMetaData> artifactMetaData = new LinkedHashSet<ComponentArtifactMetaData>();
        for (String ancestor : configurationMetaData.getHierarchy()) {
            artifactMetaData.addAll(artifactsByConfig.get(ancestor));
        }
        return artifactMetaData;
    }

    private void populateArtifactsFromDescriptor() {
        Map<Artifact, ModuleComponentArtifactMetaData> artifactToMetaData = Maps.newLinkedHashMap();
        for (Artifact descriptorArtifact : getDescriptor().getAllArtifacts()) {
            ModuleComponentArtifactMetaData artifact = artifact(descriptorArtifact);
            artifactToMetaData.put(descriptorArtifact, artifact);
        }

        artifacts = Sets.newLinkedHashSet(artifactToMetaData.values());

        this.artifactsByConfig = LinkedHashMultimap.create();
        for (String configuration : getDescriptor().getConfigurationsNames()) {
            Artifact[] configArtifacts = getDescriptor().getArtifacts(configuration);
            for (Artifact configArtifact : configArtifacts) {
                artifactsByConfig.put(configuration, artifactToMetaData.get(configArtifact));
            }
        }
    }
}
