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

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.internal.component.local.model.LocalConfigurationMetaData;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

public class DefaultIvyModulePublishMetaData implements BuildableIvyModulePublishMetaData {
    private final ModuleVersionIdentifier id;
    private final DefaultModuleDescriptor moduleDescriptor;
    private final Map<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetaData> artifactsById = new LinkedHashMap<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetaData>();

    public DefaultIvyModulePublishMetaData(ModuleVersionIdentifier id, String status) {
        this.id = id;
        moduleDescriptor = new DefaultModuleDescriptor(IvyUtil.createModuleRevisionId(id), status, null);
        moduleDescriptor.addExtraAttributeNamespace(IVY_MAVEN_NAMESPACE_PREFIX, IVY_MAVEN_NAMESPACE);
    }

    public DefaultIvyModulePublishMetaData(ModuleVersionIdentifier id, ModuleDescriptor moduleDescriptor) {
        this.id = id;
        this.moduleDescriptor = (DefaultModuleDescriptor) moduleDescriptor;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public DefaultModuleDescriptor getModuleDescriptor() {
        return moduleDescriptor;
    }

    @Override
    public void addConfiguration(LocalConfigurationMetaData configuration) {
        Set<String> extendsFrom = configuration.getExtendsFrom();
        String[] superConfigs = extendsFrom.toArray(new String[extendsFrom.size()]);
        Arrays.sort(superConfigs);
        Configuration.Visibility visibility = configuration.isVisible() ? Configuration.Visibility.PUBLIC : Configuration.Visibility.PRIVATE;
        Configuration conf = new Configuration(configuration.getName(), visibility, configuration.getDescription(), superConfigs, configuration.isTransitive(), null);
        moduleDescriptor.addConfiguration(conf);
    }

    @Override
    public void addExcludeRule(ExcludeRule excludeRule) {
        moduleDescriptor.addExcludeRule(excludeRule);
    }

    @Override
    public void addDependency(DependencyMetaData dependency) {
        ModuleRevisionId moduleRevisionId = IvyUtil.createModuleRevisionId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion());
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(moduleDescriptor, moduleRevisionId, dependency.isForce(), dependency.isChanging(), dependency.isTransitive());

        // In reality, there will only be 1 module configuration and 1 matching dependency configuration
        for (String moduleConfiguration : dependency.getModuleConfigurations()) {
            for (String dependencyConfiguration : dependency.getDependencyConfigurations(moduleConfiguration, moduleConfiguration)) {
                dependencyDescriptor.addDependencyConfiguration(moduleConfiguration, dependencyConfiguration);
            }
            addDependencyArtifacts(moduleConfiguration, dependency.getArtifacts(), dependencyDescriptor);
        }

        moduleDescriptor.addDependency(dependencyDescriptor);
    }

    private void addDependencyArtifacts(String configuration, Set<IvyArtifactName> artifacts, DefaultDependencyDescriptor dependencyDescriptor) {
        for (IvyArtifactName artifact : artifacts) {
            DefaultDependencyArtifactDescriptor artifactDescriptor = new DefaultDependencyArtifactDescriptor(
                    dependencyDescriptor, artifact.getName(), artifact.getType(), artifact.getExtension(),
                    null,
                    artifact.getClassifier() != null ? WrapUtil.toMap(Dependency.CLASSIFIER, artifact.getClassifier()) : null);
            dependencyDescriptor.addDependencyArtifact(configuration, artifactDescriptor);
        }
    }

    public void addArtifact(Artifact artifact, File file) {
        DefaultIvyModuleArtifactPublishMetaData publishMetaData = new DefaultIvyModuleArtifactPublishMetaData(id, artifact, file);
        artifactsById.put(publishMetaData.getId(), publishMetaData);
    }

    public void addArtifact(IvyModuleArtifactPublishMetaData artifact) {
        artifactsById.put(artifact.getId(), artifact);
    }

    @Override
    public void addArtifact(String configuration, PublishArtifact publishArtifact) {
        MDArtifact artifact = getOrCreate(DefaultIvyArtifactName.forPublishArtifact(publishArtifact, getId().getName()));
        artifact.addConfiguration(configuration);
        addArtifact(artifact, publishArtifact.getFile());
    }

    private MDArtifact getOrCreate(IvyArtifactName ivyName) {
        for (IvyModuleArtifactPublishMetaData artifactPublishMetaData : artifactsById.values()) {
            if (artifactPublishMetaData.getArtifactName().equals(ivyName)) {
                return (MDArtifact) artifactPublishMetaData.toIvyArtifact();
            }
        }
        return new MDArtifact(moduleDescriptor, ivyName.getName(), ivyName.getType(), ivyName.getExtension(), null, ivyArtifactAttributes(ivyName));
    }

    private Map<String, String> ivyArtifactAttributes(IvyArtifactName ivyArtifactName) {
        if (ivyArtifactName.getClassifier() == null) {
            return Collections.emptyMap();
        }
        return Collections.singletonMap("m:classifier", ivyArtifactName.getClassifier());
    }

    public Collection<IvyModuleArtifactPublishMetaData> getArtifacts() {
        return artifactsById.values();
    }

    private static class DefaultIvyModuleArtifactPublishMetaData implements IvyModuleArtifactPublishMetaData {
        private final DefaultModuleComponentArtifactIdentifier id;
        private final Artifact artifact;
        private final File file;

        private DefaultIvyModuleArtifactPublishMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact, File file) {
            this.id = new DefaultModuleComponentArtifactIdentifier(DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier), DefaultIvyArtifactName.forIvyArtifact(artifact));
            this.artifact = artifact;
            this.file = file;
        }

        public IvyArtifactName getArtifactName() {
            return id.getName();
        }

        public Artifact toIvyArtifact() {
            return artifact;
        }

        public ModuleComponentArtifactIdentifier getId() {
            return id;
        }

        public File getFile() {
            return file;
        }
    }
}
