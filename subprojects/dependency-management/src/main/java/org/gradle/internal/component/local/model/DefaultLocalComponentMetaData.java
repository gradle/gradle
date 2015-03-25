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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.model.*;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = new LinkedHashMap<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData>();
    private final Map<ArtifactRevisionId, DefaultLocalArtifactMetaData> artifactsByIvyId = new LinkedHashMap<ArtifactRevisionId, DefaultLocalArtifactMetaData>();
    private final Multimap<String, DefaultLocalArtifactMetaData> artifactsByConfig = LinkedHashMultimap.create();
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

    private void resolveArtifacts() {
        if (!artifactsResolved) {
            for (String configuration : configurationArtifacts.keySet()) {
                PublishArtifactSet artifacts = configurationArtifacts.get(configuration);
                for (PublishArtifact artifact : artifacts) {
                    addArtifact(configuration, createIvyArtifact(artifact), artifact.getFile());
                }
            }
            artifactsResolved = true;
        }
    }

    private IvyArtifactName createIvyArtifact(PublishArtifact publishArtifact) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (GUtil.isTrue(publishArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        }
        String name = publishArtifact.getName();
        if (!GUtil.isTrue(name)) {
            name = id.getName();
        }
        return new DefaultIvyArtifactName(name, publishArtifact.getType(), publishArtifact.getExtension(), extraAttributes);
    }

    void addArtifact(String configuration, IvyArtifactName artifact, File file) {
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

    // TODO:DAZ Unit test only
    public Collection<? extends LocalArtifactMetaData> getArtifacts() {
        resolveArtifacts();
        return artifactsById.values();
    }

    // TODO:DAZ Unit test only
    public LocalArtifactMetaData getArtifact(ComponentArtifactIdentifier artifactIdentifier) {
        resolveArtifacts();
        return artifactsById.get(artifactIdentifier);
    }

    public ComponentResolveMetaData toResolveMetaData() {
        return new LocalComponentResolveMetaData();
    }

    public BuildableIvyModulePublishMetaData toPublishMetaData() {
        resolveArtifacts();
        // TODO:DAZ Need to construct Ivy Artifact instances for all configurations here
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
            resolveArtifacts();
            // TODO:DAZ Work out what this actually means: I think it's used for finding the _real_ artifact that matches one declared in a dependency
            // In that case we should be inspecting the included PublishArtifact instances and building a ComponentArtifactMetaData for the matching one.
            // TODO:DAZ Maybe fail if the artifacts have not yet been built for the component
            DefaultLocalArtifactMetaData candidate = artifactsByIvyId.get(artifact.getId());
            return candidate != null ? candidate : new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), artifact, null);
        }

        // TODO:DAZ This is only used in unit tests
        public Set<ComponentArtifactMetaData> getArtifacts() {
            resolveArtifacts();
            return new LinkedHashSet<ComponentArtifactMetaData>(artifactsById.values());
        }

        @Override
        protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
            resolveArtifacts();
            // TODO:DAZ Build up from PublishArtifactSet here
            // TODO:DAZ Maybe build them all at once
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
