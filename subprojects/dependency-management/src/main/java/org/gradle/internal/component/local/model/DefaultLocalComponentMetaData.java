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

package org.gradle.internal.component.local.model;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetaData;
import org.gradle.internal.component.model.*;

import java.io.File;
import java.util.*;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = new LinkedHashMap<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData>();
    private final Map<ArtifactRevisionId, DefaultLocalArtifactMetaData> artifactsByIvyId = new LinkedHashMap<ArtifactRevisionId, DefaultLocalArtifactMetaData>();
    private final Multimap<String, DefaultLocalArtifactMetaData> artifactsByConfig = LinkedHashMultimap.create();
    private final List<DependencyMetaData> dependencies = new ArrayList<DependencyMetaData>();
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

    public void addArtifact(String configuration, IvyArtifactName artifact, File file) {
        Artifact ivyArtifact = new MDArtifact(moduleDescriptor, artifact.getName(), artifact.getType(), artifact.getExtension(), null, artifact.getAttributes());

        DefaultLocalArtifactMetaData artifactMetaData = new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), ivyArtifact, file);
        if (artifactsById.containsKey(artifactMetaData.getId())) {
            artifactMetaData = artifactsById.get(artifactMetaData.getId());
        } else {
            artifactsById.put(artifactMetaData.id, artifactMetaData);
            artifactsByIvyId.put(ivyArtifact.getId(), artifactMetaData);
        }
        moduleDescriptor.addArtifact(configuration, ivyArtifact);
        artifactsByConfig.put(configuration, artifactMetaData);
        ((MDArtifact) artifactMetaData.artifact).addConfiguration(configuration);
    }

    public void addConfiguration(String name, boolean visible, String description, String[] superConfigs, boolean transitive) {
        moduleDescriptor.addConfiguration(new Configuration(name, visible ? Configuration.Visibility.PUBLIC : Configuration.Visibility.PRIVATE, description, superConfigs, transitive, null));
    }

    public void addDependency(DependencyMetaData dependency) {
        dependencies.add(dependency);
        moduleDescriptor.addDependency(dependency.getDescriptor());
    }

    public void addExcludeRule(ExcludeRule excludeRule) {
        moduleDescriptor.addExcludeRule(excludeRule);
    }

    public Collection<? extends LocalArtifactMetaData> getArtifacts() {
        return artifactsById.values();
    }

    public LocalArtifactMetaData getArtifact(ComponentArtifactIdentifier artifactIdentifier) {
        return artifactsById.get(artifactIdentifier);
    }

    public ComponentResolveMetaData toResolveMetaData() {
        return new LocalComponentResolveMetaData();
    }

    public BuildableIvyModulePublishMetaData toPublishMetaData() {
        DefaultIvyModulePublishMetaData publishMetaData = new DefaultIvyModulePublishMetaData(id);
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

        public ModuleComponentResolveMetaData withSource(ModuleSource source) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected List<DependencyMetaData> populateDependenciesFromDescriptor() {
            return dependencies;
        }

        public ComponentArtifactMetaData artifact(Artifact artifact) {
            DefaultLocalArtifactMetaData candidate = artifactsByIvyId.get(artifact.getId());
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
