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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

import java.io.File;
import java.util.*;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = new LinkedHashMap<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData>();
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
        DefaultLocalArtifactMetaData artifactMetaData = new DefaultLocalArtifactMetaData(componentIdentifier, id, artifact, file);
        artifactsById.put(artifactMetaData.id, artifactMetaData);
        artifactsById.put(artifactMetaData.selectorId, artifactMetaData);
        artifactsByConfig.put(configuration, artifactMetaData);
    }

    public Collection<? extends LocalArtifactMetaData> getArtifacts() {
        return artifactsByConfig.values();
    }

    public LocalArtifactMetaData getArtifact(ComponentArtifactIdentifier artifactIdentifier) {
        return artifactsById.get(artifactIdentifier);
    }

    public ModuleVersionMetaData toResolveMetaData() {
        // TODO:ADAM - need to clone the descriptor
        return new AbstractModuleDescriptorBackedMetaData(id, moduleDescriptor, componentIdentifier) {
            public MutableModuleVersionMetaData copy() {
                throw new UnsupportedOperationException();
            }

            public ModuleVersionMetaData withSource(ModuleSource source) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
                Set<ComponentArtifactMetaData> result = new LinkedHashSet<ComponentArtifactMetaData>();
                Set<ModuleVersionArtifactIdentifier> seen = new HashSet<ModuleVersionArtifactIdentifier>();
                for (String configName : configurationMetaData.getHierarchy()) {
                    for (DefaultLocalArtifactMetaData localArtifactMetaData : artifactsByConfig.get(configName)) {
                        if (seen.add(localArtifactMetaData.id)) {
                            result.add(localArtifactMetaData);
                        }
                    }
                }
                return result;
            }
        };
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
        private final DefaultModuleVersionArtifactIdentifier id;
        private final ModuleVersionArtifactIdentifier selectorId;
        private final Artifact artifact;
        private final File file;

        private DefaultLocalArtifactMetaData(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact, File file) {
            this.componentIdentifier = componentIdentifier;
            // Local artifact has two 'identifiers' - The first is used to identify it uniquely, and uses (name, type, extension, file-path, custom-attrs) as the
            // identifier. Mostly these are all included for backwards compatibility. The second is used to identify the artifact when using an artifact override
            // in a project dependency. The second identifier isn't necessarily unique.
            Map<String, String> attrs = new HashMap<String, String>();
            attrs.putAll(artifact.getExtraAttributes());
            attrs.put("file", file.getAbsolutePath());
            this.id = new DefaultModuleVersionArtifactIdentifier(componentIdentifier, moduleVersionIdentifier, artifact.getName(), artifact.getType(), artifact.getExt(), attrs);
            this.selectorId = new DefaultModuleVersionArtifactIdentifier(componentIdentifier, moduleVersionIdentifier, artifact);
            this.artifact = artifact;
            this.file = file;
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
}
