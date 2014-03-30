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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

import java.io.File;
import java.util.*;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = new LinkedHashMap<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData>();
    private final Map<ArtifactRevisionId, DefaultLocalArtifactMetaData> artifactsByIvy = new LinkedHashMap<ArtifactRevisionId, DefaultLocalArtifactMetaData>();
    private final Multimap<String, DefaultLocalArtifactMetaData> artifactsByConfig = LinkedHashMultimap.create();
    private final DefaultModuleDescriptor moduleDescriptor;
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;

    public DefaultLocalComponentMetaData(DefaultModuleDescriptor moduleDescriptor, ComponentIdentifier componentIdentifier) {
        this.moduleDescriptor = moduleDescriptor;
        id = DefaultModuleVersionIdentifier.newId(moduleDescriptor.getModuleRevisionId());
        this.componentIdentifier = componentIdentifier;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public DefaultModuleDescriptor getModuleDescriptor() {
        return moduleDescriptor;
    }

    public void addArtifact(String configuration, Artifact artifact, File file) {
        moduleDescriptor.addArtifact(configuration, artifact);
        DefaultLocalArtifactMetaData artifactMetaData = new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), artifact, file);
        artifactsById.put(artifactMetaData.id, artifactMetaData);
        artifactsByConfig.put(configuration, artifactMetaData);
        artifactsByIvy.put(artifact.getId(), artifactMetaData);
    }

    public Collection<? extends LocalArtifactMetaData> getArtifacts() {
        return artifactsByConfig.values();
    }

    public LocalArtifactMetaData getArtifact(ComponentArtifactIdentifier artifactIdentifier) {
        return artifactsById.get(artifactIdentifier);
    }

    public ComponentMetaData toResolveMetaData() {
        return new LocalComponentResolveMetaData();
    }

    public BuildableModuleVersionPublishMetaData toPublishMetaData() {
        DefaultModuleVersionPublishMetaData publishMetaData = new DefaultModuleVersionPublishMetaData(id);
        for (DefaultLocalArtifactMetaData artifact : artifactsById.values()) {
            publishMetaData.addArtifact(artifact.artifact, artifact.file);
        }
        return publishMetaData;
    }

    private static class DefaultLocalArtifactMetaData implements LocalArtifactMetaData {
        private final ComponentIdentifier componentIdentifier;
        private final DefaultLocalArtifactIdentifier id;
        private final Artifact artifact;
        private final File file;

        private DefaultLocalArtifactMetaData(ComponentIdentifier componentIdentifier, String displayName, Artifact artifact, File file) {
            this.componentIdentifier = componentIdentifier;
            Map<String, String> attrs = new HashMap<String, String>();
            attrs.putAll(artifact.getExtraAttributes());
            attrs.put("file", file == null ? "null" : file.getAbsolutePath());
            this.id = new DefaultLocalArtifactIdentifier(componentIdentifier, displayName, artifact.getName(), artifact.getType(), artifact.getExt(), attrs);
            this.artifact = artifact;
            this.file = file;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public IvyArtifactName getName() {
            return id.getName();
        }

        public ComponentIdentifier getComponentId() {
            return componentIdentifier;
        }

        public ComponentArtifactIdentifier getId() {
            return id;
        }

        public File getFile() {
            return file;
        }
    }

    private class LocalComponentResolveMetaData extends AbstractModuleDescriptorBackedMetaData {
        public LocalComponentResolveMetaData() {
            // TODO:ADAM - need to clone the descriptor
            super(id, moduleDescriptor, componentIdentifier);
        }

        public MutableModuleVersionMetaData copy() {
            throw new UnsupportedOperationException();
        }

        public ModuleVersionMetaData withSource(ModuleSource source) {
            throw new UnsupportedOperationException();
        }

        public ComponentArtifactMetaData artifact(Artifact artifact) {
            DefaultLocalArtifactMetaData candidate = artifactsByIvy.get(artifact.getId());
            return candidate != null ? candidate : new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), artifact, null);
        }

        public Set<ComponentArtifactMetaData> getArtifacts() {
            return new LinkedHashSet<ComponentArtifactMetaData>(artifactsById.values());
        }

        @Override
        protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
            Set<ComponentArtifactMetaData> result = new LinkedHashSet<ComponentArtifactMetaData>();
            Set<ComponentArtifactIdentifier> seen = new HashSet<ComponentArtifactIdentifier>();
            for (String configName : configurationMetaData.getHierarchy()) {
                for (DefaultLocalArtifactMetaData localArtifactMetaData : artifactsByConfig.get(configName)) {
                    if (seen.add(localArtifactMetaData.id)) {
                        result.add(localArtifactMetaData);
                    }
                }
            }
            return result;
        }
    }
}
