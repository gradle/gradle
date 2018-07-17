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
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.List;

/**
 * Effectively immutable implementation of ConfigurationMetadata.
 * Used to represent Ivy and Maven modules in the dependency graph.
 */
public class DefaultConfigurationMetadata extends AbstractConfigurationMetadata {

    private final VariantMetadataRules componentMetadataRules;

    private List<ModuleDependencyMetadata> calculatedDependencies;

    // Could be precomputed, but we avoid doing so if attributes are never requested
    private ImmutableAttributes computedAttributes;
    private CapabilitiesMetadata computedCapabilities;

    public DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                           ImmutableList<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                           VariantMetadataRules componentMetadataRules,
                                           ImmutableList<ExcludeMetadata> excludes,
                                           ImmutableAttributes componentLevelAttributes) {
        this(componentId, name, transitive, visible, hierarchy, artifacts, componentMetadataRules, excludes, componentLevelAttributes, null);
    }

    private DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                         ImmutableList<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                         VariantMetadataRules componentMetadataRules,
                                         ImmutableList<ExcludeMetadata> excludes,
                                         ImmutableAttributes attributes,
                                         ImmutableList<ModuleDependencyMetadata> configDependencies) {
        super(componentId, name, transitive, visible, artifacts, hierarchy, excludes, attributes, configDependencies, ImmutableCapabilities.EMPTY);
        this.componentMetadataRules = componentMetadataRules;
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
        return new DefaultConfigurationMetadata(getComponentId(), getName(), isTransitive(), isVisible(), ImmutableList.copyOf(getHierarchy()), ImmutableList.copyOf(getArtifacts()), componentMetadataRules, getExcludes(), attributes, getConfigDependencies());
    }

}
