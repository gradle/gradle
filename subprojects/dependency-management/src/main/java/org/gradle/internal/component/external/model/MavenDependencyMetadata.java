/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationNotFoundException;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MavenDependencyMetadata extends DefaultDependencyMetadata {
    private final MavenScope scope;
    private final Set<String> moduleConfigurations;
    private final ImmutableList<Exclude> excludes;
    private final List<Artifact> dependencyArtifacts;

    public MavenDependencyMetadata(MavenScope scope, boolean optional, ModuleComponentSelector selector, List<Artifact> artifacts, List<Exclude> excludes) {
        super(selector, optional);
        this.scope = scope;
        if (isOptional() && scope != MavenScope.Test && scope != MavenScope.System) {
            moduleConfigurations = ImmutableSet.of("optional", scope.name().toLowerCase());
        } else {
            moduleConfigurations = ImmutableSet.of(scope.name().toLowerCase());
        }
        this.dependencyArtifacts = ImmutableList.copyOf(artifacts);
        this.excludes = ImmutableList.copyOf(excludes);
    }

    @Override
    public String toString() {
        return "dependency: " + getSelector() + ", scope: " + scope + ", optional: " + isOptional();
    }

    public MavenScope getScope() {
        return scope;
    }

    @Override
    public Set<String> getModuleConfigurations() {
        return moduleConfigurations;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public boolean isTransitive() {
        return true;
    }

    public List<ConfigurationMetadata> selectLegacyConfigurations(ComponentIdentifier fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent) {
        ImmutableList.Builder<ConfigurationMetadata> result = ImmutableList.builder();
        boolean requiresCompile = fromConfiguration.getName().equals("compile");
        if (!requiresCompile) {
            // From every configuration other than compile, include both the runtime and compile dependencies
            ConfigurationMetadata runtime = findTargetConfiguration(fromComponent, fromConfiguration, targetComponent, "runtime");
            result.add(runtime);
            requiresCompile = !runtime.getHierarchy().contains("compile");
        }
        if (requiresCompile) {
            // From compile configuration, or when the target's runtime configuration does not extend from compile, include the compile dependencies
            result.add(findTargetConfiguration(fromComponent, fromConfiguration, targetComponent, "compile"));
        }
        ConfigurationMetadata master = targetComponent.getConfiguration("master");
        if (master != null && (!master.getDependencies().isEmpty() || !master.getArtifacts().isEmpty())) {
            result.add(master);
        }
        return result.build();
    }

    private ConfigurationMetadata findTargetConfiguration(ComponentIdentifier fromComponentId, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, String target) {
        ConfigurationMetadata configuration = targetComponent.getConfiguration(target);
        if (configuration == null) {
            configuration = targetComponent.getConfiguration("default");
            if (configuration == null) {
                throw new ConfigurationNotFoundException(fromComponentId, fromConfiguration.getName(), target, targetComponent.getComponentId());
            }
        }
        return configuration;
    }

    @Override
    protected DefaultDependencyMetadata withRequested(ModuleComponentSelector newRequested) {
        return new MavenDependencyMetadata(scope, isOptional(), newRequested, getDependencyArtifacts(), excludes);
    }

    public List<Exclude> getAllExcludes() {
        return excludes;
    }

    @Override
    public List<ExcludeMetadata> getConfigurationExcludes(Collection<String> configurations) {
        return ImmutableList.<ExcludeMetadata>copyOf(excludes);
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    @Override
    public ImmutableList<IvyArtifactName> getConfigurationArtifacts(ConfigurationMetadata fromConfiguration) {
        // For a Maven dependency, the artifacts list as zero or one Artifact, always with '*' configuration
        return dependencyArtifacts.isEmpty() ? ImmutableList.<IvyArtifactName>of() : ImmutableList.of(dependencyArtifacts.get(0).getArtifactName());
    }
}
