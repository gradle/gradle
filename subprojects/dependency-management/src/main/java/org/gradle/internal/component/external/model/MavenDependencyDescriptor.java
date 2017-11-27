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
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationNotFoundException;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Represents a dependency as represented in a Maven POM file.
 */
public class MavenDependencyDescriptor extends ExternalDependencyDescriptor {
    private final ModuleComponentSelector selector;
    private final MavenScope scope;
    private final boolean optional;
    private final ImmutableList<ExcludeMetadata> excludes;

    // A dependency artifact will be defined if the descriptor specified a classifier or non-default type attribute.
    @Nullable
    private final IvyArtifactName dependencyArtifact;

    // The module configurations that this dependency applies to: should not be necessary.
    private final Set<String> moduleConfigurations;

    public MavenDependencyDescriptor(MavenScope scope, boolean optional, ModuleComponentSelector selector,
                                     @Nullable IvyArtifactName dependencyArtifact, List<ExcludeMetadata> excludes) {
        this.scope = scope;
        this.selector = selector;
        this.optional = optional;
        this.dependencyArtifact = dependencyArtifact;
        this.excludes = ImmutableList.copyOf(excludes);

        if (optional && scope != MavenScope.Test && scope != MavenScope.System) {
            moduleConfigurations = ImmutableSet.of("optional", scope.name().toLowerCase());
        } else {
            moduleConfigurations = ImmutableSet.of(scope.name().toLowerCase());
        }
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
    protected ExternalDependencyDescriptor withRequested(ModuleComponentSelector newRequested) {
        return new MavenDependencyDescriptor(scope, isOptional(), newRequested, dependencyArtifact, excludes);
    }

    public List<ExcludeMetadata> getAllExcludes() {
        return excludes;
    }

    @Override
    public List<ExcludeMetadata> getConfigurationExcludes(Collection<String> configurations) {
        return excludes;
    }

    /**
     * A Maven dependency has a 'dependency artifact' when it specifies a classifier or type attribute.
     */
    @Nullable
    public IvyArtifactName getDependencyArtifact() {
        return dependencyArtifact;
    }

    /**
     * When a Maven dependency declares a classifier or type attribute, this is modelled as a 'dependency artifact'.
     * This means that instead of resolving the default artifacts for the target dependency, we'll use the one defined
     * for the dependency.
     */
    @Override
    public ImmutableList<IvyArtifactName> getConfigurationArtifacts(ConfigurationMetadata fromConfiguration) {
        // Special handling for artifacts declared for optional dependencies
        if (isOptional()) {
            return getArtifactsForOptionalDependency(fromConfiguration);
        }
        return getDependencyArtifacts();
    }

    /**
     * When an optional dependency declares a classifier, that classifier is effectively ignored, and the optional
     * dependency will update the version of any dependency with matching GAV.
     * (Same goes for <type> on optional dependencies: they are effectively ignored).
     *
     * The exception to the optional case is when the magic "optional" configuration is being resolved.
     *
     * Note that this doesn't really match with Maven, where an optional dependency with classifier will
     * provide a version for any other dependency with matching GAV + classifier.
     */
    private ImmutableList<IvyArtifactName> getArtifactsForOptionalDependency(ConfigurationMetadata fromConfiguration) {
        if ("optional".equals(fromConfiguration.getName())) {
            return getDependencyArtifacts();
        }
        return ImmutableList.of();
    }

    /**
     * For a Maven dependency, the artifacts list as zero or one Artifact, always with '*' configuration
     */
    private ImmutableList<IvyArtifactName> getDependencyArtifacts() {
        return dependencyArtifact == null ? ImmutableList.<IvyArtifactName>of() : ImmutableList.of(dependencyArtifact);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }
}
