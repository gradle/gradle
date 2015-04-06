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

import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.*;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.model.*;

import java.io.File;
import java.util.*;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = new LinkedHashMap<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData>();
    private final Map<IvyArtifactName, DefaultLocalArtifactMetaData> artifactsByIvyName = new LinkedHashMap<IvyArtifactName, DefaultLocalArtifactMetaData>();
    private final Map<String, PublishArtifactSet> configurationArtifacts = new LinkedHashMap<String, PublishArtifactSet>();
    private final List<DependencyMetaData> dependencies = new ArrayList<DependencyMetaData>();
    private final DefaultModuleDescriptor moduleDescriptor;
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;
    private boolean artifactsResolved;

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

    public void addArtifacts(String configuration, PublishArtifactSet artifacts) {
        if (artifactsResolved) {
            throw new IllegalStateException("Cannot add artifacts after resolve");
        }
        configurationArtifacts.put(configuration, artifacts);
    }

    // TODO:DAZ We shouldn't need to 'resolve' the artifacts: just keep the configuration->PublishArtifactSet mapping for the life of this instance.
    // This mapping is the source of truth for the artifacts for this component
    private void resolveArtifacts() {
        if (!artifactsResolved) {
            for (String configuration : configurationArtifacts.keySet()) {
                PublishArtifactSet artifacts = configurationArtifacts.get(configuration);
                for (PublishArtifact artifact : artifacts) {
                    IvyArtifactName ivyArtifact = DefaultIvyArtifactName.forPublishArtifact(artifact, id.getName());
                    addArtifact(configuration, ivyArtifact, artifact.getFile());
                }
            }
            artifactsResolved = true;
        }
    }

    void addArtifact(String configuration, IvyArtifactName artifactName, File file) {
        DefaultLocalArtifactMetaData artifactMetaData = new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), artifactName, file);
        if (artifactsById.containsKey(artifactMetaData.getId())) {
            artifactMetaData = artifactsById.get(artifactMetaData.getId());
        } else {
            artifactMetaData.ivyArtifact = new MDArtifact(moduleDescriptor, artifactName.getName(), artifactName.getType(), artifactName.getExtension(), null, artifactName.getAttributes());

            artifactsById.put(artifactMetaData.id, artifactMetaData);
            // TODO:DAZ It's a bit broken that artifactMetaData.id.name != artifactName
            artifactsByIvyName.put(artifactName, artifactMetaData);
        }
        artifactMetaData.addConfiguration(configuration);

        // TODO:DAZ The Ivy Artifact is only required for publishing so that we generate the right ivy.xml file. Should change the generation code and remove this.
        moduleDescriptor.addArtifact(configuration, artifactMetaData.ivyArtifact);
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

    // TODO:DAZ This is used in unit-tests only
    public Collection<? extends LocalArtifactMetaData> getArtifacts() {
        resolveArtifacts();
        return artifactsById.values();
    }

    // TODO:DAZ This is used in unit-tests only
    public LocalArtifactMetaData getArtifact(ComponentArtifactIdentifier artifactIdentifier) {
        resolveArtifacts();
        return artifactsById.get(artifactIdentifier);
    }

    public ComponentResolveMetaData toResolveMetaData() {
        return new LocalComponentResolveMetaData();
    }

    public BuildableIvyModulePublishMetaData toPublishMetaData() {
        resolveArtifacts();
        DefaultIvyModulePublishMetaData publishMetaData = new DefaultIvyModulePublishMetaData(id);
        for (DefaultLocalArtifactMetaData artifact : artifactsById.values()) {
            IvyArtifactName artifactName = artifact.getName();
            Artifact ivyArtifact = new MDArtifact(moduleDescriptor, artifactName.getName(), artifactName.getType(), artifactName.getExtension(), null, artifactName.getAttributes());
            publishMetaData.addArtifact(ivyArtifact, artifact.file);
        }
        return publishMetaData;
    }

    private static class DefaultLocalArtifactMetaData implements LocalArtifactMetaData {
        private final ComponentIdentifier componentIdentifier;
        private final DefaultLocalArtifactIdentifier id;
        private final File file;
        private final Set<String> configurations = Sets.newHashSet();
        private MDArtifact ivyArtifact;

        private DefaultLocalArtifactMetaData(ComponentIdentifier componentIdentifier, String displayName, IvyArtifactName artifact, File file) {
            this.componentIdentifier = componentIdentifier;
            Map<String, String> attrs = new HashMap<String, String>();
            attrs.putAll(artifact.getAttributes());
            attrs.put("file", file == null ? "null" : file.getAbsolutePath());
            // TODO:DAZ The local artifact identifier should include the file directly, rather than hacking the IvyArtifactName in this way
            this.id = new DefaultLocalArtifactIdentifier(componentIdentifier, displayName, artifact.getName(), artifact.getType(), artifact.getExtension(), attrs);
            this.file = file;
        }

        void addConfiguration(String configuration) {
            configurations.add(configuration);
            ivyArtifact.addConfiguration(configuration);
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

        public ComponentArtifactMetaData artifact(IvyArtifactName ivyArtifactName) {
            resolveArtifacts();

            // TODO:DAZ Find a matching PublishArtifact and build a ComponentArtifactMetaData from that
            DefaultLocalArtifactMetaData candidate = artifactsByIvyName.get(ivyArtifactName);
            return candidate != null ? candidate : new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), ivyArtifactName, null);
        }

        // TODO:DAZ This is only used in unit tests
        public Set<ComponentArtifactMetaData> getArtifacts() {
            resolveArtifacts();
            return new LinkedHashSet<ComponentArtifactMetaData>(artifactsById.values());
        }

        @Override
        protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
            resolveArtifacts();

            Set<ComponentArtifactMetaData> result = new LinkedHashSet<ComponentArtifactMetaData>();
            for (String configName : configurationMetaData.getHierarchy()) {
                for (DefaultLocalArtifactMetaData artifactMetaData : artifactsById.values()) {
                    if (artifactMetaData.configurations.contains(configName)) {
                        result.add(artifactMetaData);
                    }
                }
            }
            return result;
        }
    }
}
