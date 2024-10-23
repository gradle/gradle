/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * A configuration representing an additional variant of a published component added by a component metadata rule.
 * It can be backed by an existing configuration/variant (base) or can initially be empty (base = null).
 */
class LazyRuleAwareWithBaseConfigurationMetadata implements ModuleConfigurationMetadata {

    private final String name;
    private final ModuleConfigurationMetadata base;
    private final ModuleComponentIdentifier componentId;
    private final VariantMetadataRules variantMetadataRules;
    private final AttributesFactory attributesFactory;
    private final ImmutableAttributes componentLevelAttributes;
    private final ImmutableList<ExcludeMetadata> excludes;
    private final boolean externalVariant;

    private List<? extends ModuleDependencyMetadata> computedDependencies;
    private ImmutableAttributes computedAttributes;
    private ImmutableCapabilities computedCapabilities;
    private ImmutableList<? extends ComponentArtifactMetadata> computedArtifacts;

    LazyRuleAwareWithBaseConfigurationMetadata(String name,
                                               @Nullable ModuleConfigurationMetadata base,
                                               ModuleComponentIdentifier componentId,
                                               AttributesFactory attributesFactory,
                                               ImmutableAttributes componentLevelAttributes,
                                               VariantMetadataRules variantMetadataRules,
                                               ImmutableList<ExcludeMetadata> excludes,
                                               boolean externalVariant) {
        this.name = name;
        this.base = base;
        this.componentId = componentId;
        this.variantMetadataRules = variantMetadataRules;
        this.attributesFactory = attributesFactory;
        this.componentLevelAttributes = componentLevelAttributes;
        this.excludes = excludes;
        this.externalVariant = externalVariant;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Identifier getIdentifier() {
        return null;
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        if (computedDependencies == null) {
            computedDependencies = variantMetadataRules.applyDependencyMetadataRules(this, base == null ? ImmutableList.of() : base.getDependencies());
        }
        return computedDependencies;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        if (computedAttributes == null) {
            computedAttributes = variantMetadataRules.applyVariantAttributeRules(this,
                base != null ? attributesFactory.concat(base.getAttributes(), componentLevelAttributes) : componentLevelAttributes);
        }
        return computedAttributes;
    }

    @Override
    public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
        if (computedArtifacts == null) {
            computedArtifacts = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts(this, base == null ? ImmutableList.of() : base.getArtifacts(), componentId);
        }
        return computedArtifacts;
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        if (computedCapabilities == null) {
            computedCapabilities = variantMetadataRules.applyCapabilitiesRules(this, base == null ? ImmutableCapabilities.EMPTY : base.getCapabilities());
        }
        return computedCapabilities;
    }

    @Override
    public Set<? extends VariantResolveMetadata> getArtifactVariants() {
        return ImmutableSet.of(new DefaultVariantMetadata(name, null, asDescribable(), getAttributes(), getArtifacts(), getCapabilities()));
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(componentId, "configuration", name);
    }

    @Override
    public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
    }

    @Override
    public ImmutableSet<String> getHierarchy() {
        return ImmutableSet.of(name);
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return excludes;
    }

    @Override
    public boolean isTransitive() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public boolean isExternalVariant() {
        return externalVariant;
    }
}
