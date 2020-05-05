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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Effectively immutable implementation of ConfigurationMetadata.
 * Used to represent Ivy and Maven modules in the dependency graph.
 */
public class DefaultConfigurationMetadata extends AbstractConfigurationMetadata {

    private final VariantMetadataRules componentMetadataRules;

    private List<ModuleDependencyMetadata> calculatedDependencies;
    private ImmutableList<? extends ModuleComponentArtifactMetadata> calculatedArtifacts;

    // Could be precomputed, but we avoid doing so if attributes are never requested
    private ImmutableAttributes computedAttributes;
    private CapabilitiesMetadata computedCapabilities;

    // Fields used for performance optimizations: we avoid computing the derived dependencies (withConstraints, withoutConstraints, ...)
    // eagerly because it's very likely that those methods would only be called on the selected variant. Therefore it's a waste of time
    // to compute them eagerly when those filtering methods are called. We cannot use a dedicated, lazy wrapper over configuration metadata
    // because we need the attributes to be computes lazily too, because of component metadata rules.
    private final DependencyFilter dependencyFilter;
    private ImmutableList<ModuleDependencyMetadata> filteredConfigDependencies;

    public DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                        ImmutableSet<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                        VariantMetadataRules componentMetadataRules,
                                        ImmutableList<ExcludeMetadata> excludes,
                                        ImmutableAttributes componentLevelAttributes,
                                        boolean mavenArtifactDiscovery) {
        super(componentId, name, transitive, visible, artifacts, hierarchy, excludes, componentLevelAttributes, (ImmutableList<ModuleDependencyMetadata>) null, ImmutableCapabilities.EMPTY, mavenArtifactDiscovery);
        this.componentMetadataRules = componentMetadataRules;
        this.dependencyFilter = DependencyFilter.ALL;
    }

    private DefaultConfigurationMetadata(ModuleComponentIdentifier componentId,
                                         String name,
                                         boolean transitive,
                                         boolean visible,
                                         ImmutableSet<String> hierarchy,
                                         ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                         VariantMetadataRules componentMetadataRules,
                                         ImmutableList<ExcludeMetadata> excludes,
                                         ImmutableAttributes attributes,
                                         Factory<List<ModuleDependencyMetadata>> configDependenciesFactory,
                                         DependencyFilter dependencyFilter,
                                         CapabilitiesMetadata capabilities,
                                         boolean mavenArtifactDiscovery) {
        super(componentId, name, transitive, visible, artifacts, hierarchy, excludes, attributes, configDependenciesFactory, ImmutableCapabilities.of(capabilities), mavenArtifactDiscovery);
        this.componentMetadataRules = componentMetadataRules;
        this.dependencyFilter = dependencyFilter;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        if (computedAttributes == null) {
            computedAttributes = componentMetadataRules.applyVariantAttributeRules(this, super.getAttributes());
        }
        return computedAttributes;
    }

    @Override
    ImmutableList<ModuleDependencyMetadata> getConfigDependencies() {
        if (filteredConfigDependencies != null) {
            return filteredConfigDependencies;
        }
        ImmutableList<ModuleDependencyMetadata> filtered = super.getConfigDependencies();
        switch (dependencyFilter) {
            case CONSTRAINTS_ONLY:
            case FORCED_CONSTRAINTS_ONLY:
                filtered = withConstraints(true, filtered);
                break;
            case DEPENDENCIES_ONLY:
            case FORCED_DEPENDENCIES_ONLY:
                filtered = withConstraints(false, filtered);
                break;
        }
        switch (dependencyFilter) {
            case FORCED_ALL:
            case FORCED_CONSTRAINTS_ONLY:
            case FORCED_DEPENDENCIES_ONLY:
                filtered = force(filtered);
                break;
        }
        filteredConfigDependencies = filtered;
        return filteredConfigDependencies;
    }

    private ImmutableList<? extends ModuleComponentArtifactMetadata> getOriginalArtifacts() {
        return super.getArtifacts();
    }

    @Override
    public ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifacts() {
        if (calculatedArtifacts == null) {
            calculatedArtifacts = componentMetadataRules.applyVariantFilesMetadataRulesToArtifacts(this, getOriginalArtifacts(), getComponentId());
        }
        return calculatedArtifacts;
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        if (calculatedDependencies == null) {
            calculatedDependencies = componentMetadataRules.applyDependencyMetadataRules(this, getConfigDependencies());
        }
        return calculatedDependencies;
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        if (computedCapabilities == null) {
            computedCapabilities = componentMetadataRules.applyCapabilitiesRules(this, super.getCapabilities());
        }
        return computedCapabilities;
    }

    @Override
    public boolean requiresMavenArtifactDiscovery() {
        // If artifacts are computed, we opt-out of artifact discovery
        return super.requiresMavenArtifactDiscovery() && getArtifacts() == getOriginalArtifacts();
    }

    private Factory<List<ModuleDependencyMetadata>> lazyConfigDependencies() {
        return new Factory<List<ModuleDependencyMetadata>>() {
            @Nullable
            @Override
            public List<ModuleDependencyMetadata> create() {
                return DefaultConfigurationMetadata.super.getConfigDependencies();
            }
        };
    }

    private ImmutableList<ModuleDependencyMetadata> force(ImmutableList<ModuleDependencyMetadata> configDependencies) {
        ImmutableList.Builder<ModuleDependencyMetadata> dependencies = new ImmutableList.Builder<>();
        for (ModuleDependencyMetadata configDependency : configDependencies) {
            if (configDependency instanceof ForcingDependencyMetadata) {
                dependencies.add((ModuleDependencyMetadata) ((ForcingDependencyMetadata) configDependency).forced());
            } else {
                dependencies.add(new ForcedDependencyMetadataWrapper(configDependency));
            }
        }
        return dependencies.build();
    }

    private ImmutableList<ModuleDependencyMetadata> withConstraints(boolean constraint, ImmutableList<ModuleDependencyMetadata> configDependencies) {
        if (configDependencies.isEmpty()) {
            return ImmutableList.of();
        }
        int count = 0;
        ImmutableList.Builder<ModuleDependencyMetadata> filtered = null;
        for (ModuleDependencyMetadata configDependency : configDependencies) {
            if (configDependency.isConstraint() == constraint) {
                if (filtered == null) {
                    filtered = new ImmutableList.Builder<>();
                }
                filtered.add(configDependency);
                count++;
            }
        }
        if (count == configDependencies.size()) {
            // Avoid creating a copy if the resulting configuration is identical
            return configDependencies;
        }
        return filtered == null ? ImmutableList.of() : filtered.build();
    }

    public Builder mutate() {
        return new Builder();
    }

    private enum DependencyFilter {
        ALL,
        CONSTRAINTS_ONLY,
        DEPENDENCIES_ONLY,
        FORCED_ALL,
        FORCED_CONSTRAINTS_ONLY,
        FORCED_DEPENDENCIES_ONLY;

        DependencyFilter forcing() {
            switch (this) {
                case ALL:
                    return FORCED_ALL;
                case CONSTRAINTS_ONLY:
                    return FORCED_CONSTRAINTS_ONLY;
                case DEPENDENCIES_ONLY:
                    return FORCED_DEPENDENCIES_ONLY;
            }
            return this;
        }

        DependencyFilter dependenciesOnly() {
            switch (this) {
                case ALL:
                    return DEPENDENCIES_ONLY;
                case FORCED_ALL:
                    return FORCED_DEPENDENCIES_ONLY;
                case DEPENDENCIES_ONLY:
                    return this;
            }
            throw new IllegalStateException("Cannot set dependencies only when constraints only has already been called");
        }

        DependencyFilter constraintsOnly() {
            switch (this) {
                case ALL:
                    return CONSTRAINTS_ONLY;
                case FORCED_ALL:
                    return FORCED_CONSTRAINTS_ONLY;
                case CONSTRAINTS_ONLY:
                    return this;
            }
            throw new IllegalStateException("Cannot set constraints only when dependencies only has already been called");
        }
    }

    public class Builder {
        private String name = DefaultConfigurationMetadata.this.getName();
        private DependencyFilter dependencyFilter = DependencyFilter.ALL;
        private CapabilitiesMetadata capabilities;
        private ImmutableAttributes attributes;
        private ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;

        Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withArtifacts(ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts) {
            this.artifacts = artifacts;
            return this;
        }

        Builder withAttributes(ImmutableAttributes attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder withoutConstraints() {
            dependencyFilter = dependencyFilter.dependenciesOnly();
            return this;
        }

        Builder withForcedDependencies() {
            dependencyFilter = dependencyFilter.forcing();
            return this;
        }

        Builder withConstraintsOnly() {
            dependencyFilter = dependencyFilter.constraintsOnly();
            artifacts = ImmutableList.of();
            return this;
        }

        Builder withCapabilities(CapabilitiesMetadata capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public DefaultConfigurationMetadata build() {
            return new DefaultConfigurationMetadata(
                    getComponentId(),
                    name,
                    isTransitive(),
                    isVisible(),
                    getHierarchy(),
                    artifacts == null ? DefaultConfigurationMetadata.this.getOriginalArtifacts() : artifacts,
                    componentMetadataRules,
                    getExcludes(),
                    attributes == null ? DefaultConfigurationMetadata.super.getAttributes() : attributes,
                    lazyConfigDependencies(),
                    dependencyFilter,
                    capabilities == null ? DefaultConfigurationMetadata.this.getCapabilities() : capabilities,
                    DefaultConfigurationMetadata.super.requiresMavenArtifactDiscovery()
            );
        }
    }
}
