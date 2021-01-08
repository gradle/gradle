/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.component.external.ivypublish;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.internal.Pair;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIvyModulePublishMetadata implements IvyModulePublishMetadata {
    private static final Transformer<String, String> VERSION_TRANSFORMER = new IvyVersionTransformer();
    private final ModuleComponentIdentifier id;
    private final String status;
    private final Map<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetadata> artifactsById = new LinkedHashMap<>();
    private final Map<String, Configuration> configurations = new LinkedHashMap<>();
    private final Set<LocalOriginDependencyMetadata> dependencies = new LinkedHashSet<>();
    private final List<Pair<ExcludeMetadata, String>> excludes = Lists.newArrayList();

    public DefaultIvyModulePublishMetadata(ModuleComponentIdentifier id, String status) {
        this.id = id;
        this.status = status;
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return id;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public Map<String, Configuration> getConfigurations() {
        return configurations;
    }

    @Override
    public Collection<LocalOriginDependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public List<Pair<ExcludeMetadata, String>> getExcludes() {
        return excludes;
    }

    public BuildableLocalConfigurationMetadata addConfiguration(String name, Set<String> extendsFrom, boolean visible, boolean transitive) {
        List<String> sortedExtends = Lists.newArrayList(extendsFrom);
        Collections.sort(sortedExtends);
        Configuration configuration = new Configuration(name, transitive, visible, sortedExtends);
        configurations.put(name, configuration);
        return new ConfigurationMetadata(name);
    }

    /**
     * [1.0] is a valid version in maven, but not in Ivy: strip the surrounding '[' and ']' characters for ivy publish.
     */
    private static LocalOriginDependencyMetadata normalizeVersionForIvy(LocalOriginDependencyMetadata dependency) {
        if (dependency.getSelector() instanceof ModuleComponentSelector) {
            ModuleComponentSelector selector = (ModuleComponentSelector) dependency.getSelector();
            VersionConstraint versionConstraint = selector.getVersionConstraint();
            DefaultImmutableVersionConstraint transformedConstraint =
                new DefaultImmutableVersionConstraint(
                    VERSION_TRANSFORMER.transform(versionConstraint.getPreferredVersion()),
                    VERSION_TRANSFORMER.transform(versionConstraint.getRequiredVersion()),
                    VERSION_TRANSFORMER.transform(versionConstraint.getStrictVersion()),
                    CollectionUtils.collect(versionConstraint.getRejectedVersions(), VERSION_TRANSFORMER),
                    versionConstraint.getBranch());
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), transformedConstraint, selector.getAttributes(), selector.getRequestedCapabilities());
            return dependency.withTarget(newSelector);
        }
        return dependency;
    }

    public void addArtifact(IvyArtifactName artifact, File file) {
        DefaultIvyModuleArtifactPublishMetadata publishMetadata = new DefaultIvyModuleArtifactPublishMetadata(id, artifact);
        publishMetadata.setFile(file);
        artifactsById.put(publishMetadata.getId(), publishMetadata);
    }

    public void addArtifact(IvyModuleArtifactPublishMetadata artifact) {
        artifactsById.put(artifact.getId(), artifact);
    }

    private DefaultIvyModuleArtifactPublishMetadata getOrCreate(IvyArtifactName ivyName) {
        for (IvyModuleArtifactPublishMetadata artifactPublishMetadata : artifactsById.values()) {
            if (artifactPublishMetadata.getArtifactName().equals(ivyName)) {
                return (DefaultIvyModuleArtifactPublishMetadata) artifactPublishMetadata;
            }
        }
        DefaultIvyModuleArtifactPublishMetadata artifact = new DefaultIvyModuleArtifactPublishMetadata(id, ivyName);
        artifactsById.put(artifact.getId(), artifact);
        return artifact;
    }

    @Override
    public Collection<IvyModuleArtifactPublishMetadata> getArtifacts() {
        return artifactsById.values();
    }

    public void addArtifact(String configuration, PublishArtifact artifact) {
        DefaultIvyArtifactName ivyName = DefaultIvyArtifactName.forPublishArtifact(artifact);
        DefaultIvyModuleArtifactPublishMetadata ivyArtifact = getOrCreate(ivyName);
        ivyArtifact.setFile(artifact.getFile());
        ivyArtifact.addConfiguration(configuration);
    }

    private class ConfigurationMetadata implements BuildableLocalConfigurationMetadata {
        private final String configurationName;

        private ConfigurationMetadata(String configurationName) {
            this.configurationName = configurationName;
        }

        @Override
        public ComponentIdentifier getComponentId() {
            return id;
        }

        @Override
        public void addDependency(LocalOriginDependencyMetadata dependency) {
            assert dependency.getModuleConfiguration().equals(configurationName);
            dependencies.add(normalizeVersionForIvy(dependency));
        }

        @Override
        public void addExclude(ExcludeMetadata exclude) {
            excludes.add(Pair.of(exclude, configurationName));
        }

        @Override
        public void addFiles(LocalFileDependencyMetadata files) {
            // Ignore files
        }

        @Override
        public void enableLocking() {
            // Ignore
        }
    }

    private static class IvyVersionTransformer implements Transformer<String, String> {
        @Override
        public String transform(String version) {
            if (version != null && version.startsWith("[") && version.endsWith("]") && version.indexOf(',') == -1) {
                return version.substring(1, version.length() - 1);
            }
            return version;
        }
    }
}
