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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;

/**
 * Effectively immutable implementation of ConfigurationMetadata.
 * Used to represent Ivy and Maven modules in the dependency graph.
 */
public class DefaultConfigurationMetadata extends AbstractConfigurationMetadata {

    private final VariantMetadataRules componentMetadataRules;

    private List<ModuleDependencyMetadata> calculatedDependencies;

    private final ImmutableAttributes componentLevelAttributes;

    // Could be precomputed, but we avoid doing so if attributes are never requested
    private ImmutableAttributes computedAttributes;
    private CapabilitiesMetadata computedCapabilities;

    public DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                        ImmutableSet<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                        VariantMetadataRules componentMetadataRules,
                                        ImmutableList<ExcludeMetadata> excludes,
                                        ImmutableAttributes componentLevelAttributes) {
        this(componentId, name, transitive, visible, hierarchy, artifacts, componentMetadataRules, excludes, componentLevelAttributes, null);
    }

    private DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                         ImmutableSet<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                         VariantMetadataRules componentMetadataRules,
                                         ImmutableList<ExcludeMetadata> excludes,
                                         ImmutableAttributes attributes,
                                         ImmutableList<ModuleDependencyMetadata> configDependencies) {
        super(componentId, name, transitive, visible, artifacts, hierarchy, excludes, attributes, configDependencies, ImmutableCapabilities.EMPTY);
        this.componentMetadataRules = componentMetadataRules;
        this.componentLevelAttributes = attributes;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        if (computedAttributes == null) {
            computedAttributes = componentMetadataRules.applyVariantAttributeRules(this, super.getAttributes());
        }
        return computedAttributes;
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
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

    public DefaultConfigurationMetadata withAttributes(ImmutableAttributes attributes) {
        return new DefaultConfigurationMetadata(getComponentId(), getName(), isTransitive(), isVisible(), getHierarchy(), getArtifacts(), componentMetadataRules, getExcludes(), attributes, getConfigDependencies());
    }

    public DefaultConfigurationMetadata withAttributes(String newName, ImmutableAttributes attributes) {
        return new DefaultConfigurationMetadata(getComponentId(), newName, isTransitive(), isVisible(), getHierarchy(), getArtifacts(), componentMetadataRules, getExcludes(), attributes, getConfigDependencies());
    }

    public DefaultConfigurationMetadata withForcedDependencies() {
        ImmutableList<ModuleDependencyMetadata> configDependencies = getConfigDependencies();
        if (configDependencies.isEmpty()) {
            return this;
        }
        return new DefaultConfigurationMetadata(getComponentId(), getName(), isTransitive(), isVisible(), getHierarchy(), getArtifacts(), componentMetadataRules, getExcludes(), componentLevelAttributes, force(configDependencies));
    }

    private ImmutableList<ModuleDependencyMetadata> force(ImmutableList<ModuleDependencyMetadata> configDependencies) {
        ImmutableList.Builder<ModuleDependencyMetadata> dependencies = new ImmutableList.Builder<ModuleDependencyMetadata>();
        for (ModuleDependencyMetadata configDependency : configDependencies) {
            if (configDependency instanceof ForcingDependencyMetadata) {
                dependencies.add((ModuleDependencyMetadata) ((ForcingDependencyMetadata) configDependency).forced());
            } else {
                dependencies.add(new ForcedDependencyMetadataWrapper(configDependency));
            }
        }
        return dependencies.build();
    }

    public DefaultConfigurationMetadata withoutConstraints() {
        return withConstraints(false);
    }

    public DefaultConfigurationMetadata withConstraintsOnly() {
        return withConstraints(true);
    }

    private DefaultConfigurationMetadata withConstraints(boolean constraint) {
        ImmutableList<ModuleDependencyMetadata> configDependencies = getConfigDependencies();
        if (configDependencies.isEmpty()) {
            return this;
        }
        int count = 0;
        ImmutableList.Builder<ModuleDependencyMetadata> filtered = null;
        for (ModuleDependencyMetadata configDependency : configDependencies) {
            if (configDependency.isConstraint() == constraint) {
                if (filtered == null) {
                    filtered = new ImmutableList.Builder<ModuleDependencyMetadata>();
                }
                filtered.add(configDependency);
                count++;
            }
        }
        if (count == configDependencies.size()) {
            // Avoid creating a copy if the resulting configuration is identical
            return this;
        }
        ImmutableList<ModuleDependencyMetadata> filteredDependencies = filtered == null ? ImmutableList.<ModuleDependencyMetadata>of() : filtered.build();
        return new DefaultConfigurationMetadata(getComponentId(), getName(), isTransitive(), isVisible(), getHierarchy(), getArtifacts(), componentMetadataRules, getExcludes(), componentLevelAttributes, filteredDependencies);
    }

    private static class ForcedDependencyMetadataWrapper implements ForcingDependencyMetadata, ModuleDependencyMetadata {
        private final ModuleDependencyMetadata delegate;

        private ForcedDependencyMetadataWrapper(ModuleDependencyMetadata delegate) {
            this.delegate = delegate;
        }

        @Override
        public ModuleComponentSelector getSelector() {
            return delegate.getSelector();
        }

        @Override
        public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
            return new ForcedDependencyMetadataWrapper(delegate.withRequestedVersion(requestedVersion));
        }

        @Override
        public ModuleDependencyMetadata withReason(String reason) {
            return new ForcedDependencyMetadataWrapper(delegate.withReason(reason));
        }

        @Override
        public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
            return delegate.selectConfigurations(consumerAttributes, targetComponent, consumerSchema);
        }

        @Override
        public List<ExcludeMetadata> getExcludes() {
            return delegate.getExcludes();
        }

        @Override
        public List<IvyArtifactName> getArtifacts() {
            return delegate.getArtifacts();
        }

        @Override
        public DependencyMetadata withTarget(ComponentSelector target) {
            return new ForcedDependencyMetadataWrapper((ModuleDependencyMetadata) delegate.withTarget(target));
        }

        @Override
        public boolean isChanging() {
            return delegate.isChanging();
        }

        @Override
        public boolean isTransitive() {
            return delegate.isTransitive();
        }

        @Override
        public boolean isConstraint() {
            return delegate.isConstraint();
        }

        @Override
        public String getReason() {
            return delegate.getReason();
        }

        @Override
        public boolean isForce() {
            return true;
        }

        @Override
        public ForcingDependencyMetadata forced() {
            return this;
        }
    }
}
