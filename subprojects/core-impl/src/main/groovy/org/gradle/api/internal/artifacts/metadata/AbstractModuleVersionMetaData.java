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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

import java.util.*;

abstract class AbstractModuleVersionMetaData extends AbstractModuleDescriptorBackedMetaData implements MutableModuleVersionMetaData {
    private Set<ModuleVersionArtifactMetaData> artifacts;

    public AbstractModuleVersionMetaData(ModuleDescriptor moduleDescriptor) {
        this(moduleVersionIdentifier(moduleDescriptor), moduleDescriptor, moduleComponentIdentifier(moduleDescriptor));
    }
    
    private static ModuleVersionIdentifier moduleVersionIdentifier(ModuleDescriptor descriptor) {
        return DefaultModuleVersionIdentifier.newId(descriptor.getModuleRevisionId());
    }

    private static ModuleComponentIdentifier moduleComponentIdentifier(ModuleDescriptor descriptor) {
        return DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier(descriptor));
    }

    public AbstractModuleVersionMetaData(ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentIdentifier) {
        super(moduleVersionIdentifier, moduleDescriptor, componentIdentifier);
    }

    public abstract AbstractModuleVersionMetaData copy();

    public ModuleVersionMetaData withSource(ModuleSource source) {
        AbstractModuleVersionMetaData copy = copy();
        copy.setModuleSource(source);
        return copy;
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return (ModuleComponentIdentifier) super.getComponentId();
    }

    public ModuleVersionArtifactMetaData artifact(Artifact artifact) {
        return new DefaultModuleVersionArtifactMetaData(getComponentId(), artifact);
    }

    public ModuleVersionArtifactMetaData artifact(String type, @Nullable String extension, @Nullable String classifier) {
        Map extraAttributes = classifier == null ? Collections.emptyMap() : Collections.singletonMap("m:classifier", classifier);
        Artifact artifact = new DefaultArtifact(getDescriptor().getModuleRevisionId(), null, getId().getName(), type, extension, extraAttributes);
        return new DefaultModuleVersionArtifactMetaData(getComponentId(), artifact);
    }

    public Set<ModuleVersionArtifactMetaData> getArtifacts() {
        if (artifacts == null) {
            artifacts = new LinkedHashSet<ModuleVersionArtifactMetaData>();
            for (Artifact artifact : getDescriptor().getAllArtifacts()) {
                artifacts.add(artifact(artifact));
            }
        }
        return artifacts;
    }

    protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
        Set<Artifact> artifacts = new HashSet<Artifact>();
        Set<ComponentArtifactMetaData> artifactMetaData = new LinkedHashSet<ComponentArtifactMetaData>();
        for (String ancestor : configurationMetaData.getHierarchy()) {
            for (Artifact artifact : getDescriptor().getArtifacts(ancestor)) {
                if (artifacts.add(artifact)) {
                    artifactMetaData.add(new DefaultModuleVersionArtifactMetaData(getComponentId(), artifact));
                }
            }
        }
        return artifactMetaData;
    }

}
