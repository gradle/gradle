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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.NoOpDerivationStrategy;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class LenientPlatformResolveMetadata implements ModuleComponentResolveMetadata {

    private final ModuleComponentIdentifier moduleComponentIdentifier;
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final VirtualPlatformState platformState;
    private final NodeState platformNode;
    private final ResolveState resolveState;

    LenientPlatformResolveMetadata(ModuleComponentIdentifier moduleComponentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, VirtualPlatformState platformState, NodeState platformNode, ResolveState resolveState) {
        this.moduleComponentIdentifier = moduleComponentIdentifier;
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.platformState = platformState;
        this.platformNode = platformNode;
        this.resolveState = resolveState;
    }

    @Override
    public ModuleComponentIdentifier getId() {
        return moduleComponentIdentifier;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionIdentifier;
    }

    @Override
    public ModuleSources getSources() {
        return ImmutableModuleSources.of();
    }

    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return EmptySchema.INSTANCE;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return Collections.singleton("default");
    }

    LenientPlatformResolveMetadata withVersion(ModuleComponentIdentifier moduleComponentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return new LenientPlatformResolveMetadata(moduleComponentIdentifier, moduleVersionIdentifier, platformState, platformNode, resolveState);
    }

    @Nullable
    @Override
    public ModuleConfigurationMetadata getConfiguration(String name) {
        if ("default".equals(name)) {
            ImmutableList.Builder<ModuleDependencyMetadata> dependencies = new ImmutableList.Builder<>();
            Set<ModuleResolveState> participatingModules = platformState.getParticipatingModules();
            for (ModuleResolveState module : participatingModules) {
                dependencies.add(new LenientPlatformDependencyMetadata(
                    resolveState,
                    platformNode,
                    DefaultModuleComponentSelector.newSelector(module.getId(), moduleVersionIdentifier.getVersion()),
                    DefaultModuleComponentIdentifier.newId(module.getId(), moduleVersionIdentifier.getVersion()),
                    platformState.getSelectedPlatformId(),
                    platformState.isForced(),
                    true
                ));
            }
            return new RealisedConfigurationMetadata(
                moduleComponentIdentifier, name, false, false,
                ImmutableSet.of(name), ImmutableList.of(), ImmutableList.of(), ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, dependencies.build(), false, false
            );
        }
        throw new IllegalArgumentException("Undefined configuration '" + name + "'");
    }

    @Override
    public Optional<List<? extends VariantGraphResolveMetadata>> getVariantsForGraphTraversal() {
        return Optional.absent();
    }

    @Override
    public boolean isMissing() {
        return false;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public List<String> getStatusScheme() {
        return null;
    }

    @Override
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

    @Override
    public MutableModuleComponentResolveMetadata asMutable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleComponentResolveMetadata withSources(ModuleSources sources) {
        return this;
    }

    @Override
    public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
        return this;
    }

    @Override
    public VariantDerivationStrategy getVariantDerivationStrategy() {
        return NoOpDerivationStrategy.getInstance();
    }

    @Override
    public boolean isExternalVariant() {
        return false;
    }

    @Override
    public boolean isComponentMetadataRuleCachingEnabled() {
        return true;
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleComponentArtifactMetadata optionalArtifact(String type, @Nullable String extension, @Nullable String classifier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        return ImmutableList.of();
    }

    @Override
    public ImmutableAttributesFactory getAttributesFactory() {
        return null;
    }

    @Override
    public VariantMetadataRules getVariantMetadataRules() {
        return VariantMetadataRules.noOp();
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return ImmutableAttributes.EMPTY;
    }

    VirtualPlatformState getPlatformState() {
        return platformState;
    }
}
