/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.model.ivy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class IvyConfigurationHelper {

    private final ImmutableList<Artifact> artifactDefinitions;
    private final Map<Artifact, ModuleComponentArtifactMetadata> artifacts;
    private final ImmutableList<Exclude> excludes;
    private final ImmutableList<IvyDependencyDescriptor> dependencies;
    private final ModuleComponentIdentifier componentId;

    IvyConfigurationHelper(ImmutableList<Artifact> artifactDefinitions, Map<Artifact, ModuleComponentArtifactMetadata> artifacts, ImmutableList<Exclude> excludes, ImmutableList<IvyDependencyDescriptor> dependencies, ModuleComponentIdentifier componentId) {

        this.artifactDefinitions = artifactDefinitions;
        this.artifacts = artifacts;
        this.excludes = excludes;
        this.dependencies = dependencies;
        this.componentId = componentId;
    }

    ImmutableList<ModuleComponentArtifactMetadata> filterArtifacts(String name, Collection<String> hierarchy) {
        Set<ModuleComponentArtifactMetadata> artifacts = new LinkedHashSet<>();
        collectArtifactsFor(name, artifacts);
        for (String parent : hierarchy) {
            collectArtifactsFor(parent, artifacts);
        }
        return ImmutableList.copyOf(artifacts);
    }

    private void collectArtifactsFor(String name, Collection<ModuleComponentArtifactMetadata> dest) {
        for (Artifact artifact : artifactDefinitions) {
            if (artifact.getConfigurations().contains(name)) {
                ModuleComponentArtifactMetadata artifactMetadata = artifacts.get(artifact);
                if (artifactMetadata == null) {
                    artifactMetadata = new DefaultModuleComponentArtifactMetadata(componentId, artifact.getArtifactName());
                    artifacts.put(artifact, artifactMetadata);
                }
                dest.add(artifactMetadata);
            }
        }
    }

    ImmutableList<ExcludeMetadata> filterExcludes(ImmutableSet<String> hierarchy) {
        ImmutableList.Builder<ExcludeMetadata> filtered = ImmutableList.builder();
        for (Exclude exclude : excludes) {
            for (String config : exclude.getConfigurations()) {
                if (hierarchy.contains(config)) {
                    filtered.add(exclude);
                    break;
                }
            }
        }
        return filtered.build();
    }

    ImmutableList<ModuleDependencyMetadata> filterDependencies(ConfigurationMetadata config) {
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = ImmutableList.builder();
        for (IvyDependencyDescriptor dependency : dependencies) {
            if (include(dependency, config.getName(), config.getHierarchy())) {
                filteredDependencies.add(contextualize(config, componentId, dependency));
            }
        }
        return filteredDependencies.build();
    }

    ModuleDependencyMetadata contextualize(ConfigurationMetadata config, ModuleComponentIdentifier componentId, IvyDependencyDescriptor incoming) {
        return new ConfigurationBoundExternalDependencyMetadata(config, componentId, incoming);
    }

    private boolean include(IvyDependencyDescriptor dependency, String configName, Collection<String> hierarchy) {
        Set<String> dependencyConfigurations = dependency.getConfMappings().keySet();
        for (String moduleConfiguration : dependencyConfigurations) {
            if (moduleConfiguration.equals("%") || hierarchy.contains(moduleConfiguration)) {
                return true;
            }
            if (moduleConfiguration.equals("*")) {
                boolean include = true;
                for (String conf2 : dependencyConfigurations) {
                    if (conf2.startsWith("!") && conf2.substring(1).equals(configName)) {
                        include = false;
                        break;
                    }
                }
                if (include) {
                    return true;
                }
            }
        }
        return false;
    }
}
