/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ExternalModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Represents a dependency declared in an Ivy descriptor file.
 * <p>
 * This dependency metadata is bound to a source configuration, since Ivy resolves
 * target components differently based on the configuration that they are sourced from.
 * </p>
 */
public class IvyDependencyMetadata extends ExternalModuleDependencyMetadata {

    private final ConfigurationMetadata configuration;
    private final IvyDependencyDescriptor dependencyDescriptor;

    public IvyDependencyMetadata(ConfigurationMetadata configuration, IvyDependencyDescriptor dependencyDescriptor) {
        this(configuration, dependencyDescriptor, null, false);
    }

    public IvyDependencyMetadata(ConfigurationMetadata configuration, IvyDependencyDescriptor dependencyDescriptor, @Nullable String reason, boolean endorsing) {
        this(configuration, dependencyDescriptor, reason, endorsing, dependencyDescriptor.getConfigurationArtifacts(configuration));
    }

    private IvyDependencyMetadata(ConfigurationMetadata configuration, IvyDependencyDescriptor dependencyDescriptor, @Nullable String reason, boolean endorsing, List<IvyArtifactName> artifacts) {
        super(reason, endorsing, artifacts);
        this.configuration = configuration;
        this.dependencyDescriptor = dependencyDescriptor;
    }

    @Override
    public IvyDependencyDescriptor getDependencyDescriptor() {
        return dependencyDescriptor;
    }

    @Override
    protected GraphVariantSelectionResult selectLegacyConfigurations(
        GraphVariantSelector variantSelector,
        ImmutableAttributes consumerAttributes,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema
    ) {
        // We only want to use ivy's configuration selection mechanism when an ivy component is selecting
        // configurations from another ivy component.
        if (targetComponentState instanceof IvyComponentGraphResolveState) {
            IvyComponentGraphResolveState ivyComponent = (IvyComponentGraphResolveState) targetComponentState;
            return getDependencyDescriptor().selectLegacyConfigurations(configuration, ivyComponent, variantSelector.getFailureHandler());
        }

        // We have already verified that the target component does not support attribute matching,
        // so if it is not an ivy component, use the standard legacy selection mechanism.

        // TODO: We check hasLegacyVariant so we can fall-back to the deprecated behavior in case the legacy variant
        // is present but non-consumable. Once we remove the deprecation, we can avoid this check and allow
        // selectLegacyVariant to throw an exception if there is no legacy variant.
        boolean hasLegacyVariant = targetComponentState.getCandidatesForGraphVariantSelection().getLegacyVariant() != null;
        if (hasLegacyVariant) {
            VariantGraphResolveState selected = variantSelector.selectLegacyVariant(consumerAttributes, targetComponentState, consumerSchema, variantSelector.getFailureHandler());
            return new GraphVariantSelectionResult(Collections.singletonList(selected), false);
        }

        // Perhaps the legacy variant is present, but comes from a non-consumable configuration.
        if (targetComponentState instanceof LocalComponentGraphResolveState) {
            LocalComponentGraphResolveState localComponent = (LocalComponentGraphResolveState) targetComponentState;

            // getConfigurationLegacy is a legacy mechanism and does _not_ check if the target variant comes from a consumable configuration
            @SuppressWarnings("deprecation")
            LocalVariantGraphResolveState legacyVariant = localComponent.getConfigurationLegacy(Dependency.DEFAULT_CONFIGURATION);
            if (legacyVariant != null) {
                // The legacy variant is present, but comes from a non-consumable configuration.

                DeprecationLogger.deprecateBehaviour("Consuming non-consumable variants from from an ivy component.")
                    .willBecomeAnErrorInGradle9()
                    .withUpgradeGuideSection(8, "consuming_non_consumable_variants_from_ivy_component")
                    .nagUser();

                return new GraphVariantSelectionResult(Collections.singletonList(legacyVariant), false);
            }
        }

        // The variant was not present, even after checking for a legacy non-consumable version. We can fail now.
        throw variantSelector.getFailureHandler().noVariantsExistFailure(consumerSchema, consumerAttributes, targetComponentState.getId());
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return getDependencyDescriptor().getConfigurationExcludes(configuration.getHierarchy());
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return new IvyDependencyMetadata(configuration, dependencyDescriptor, reason, isEndorsingStrictVersions(), getArtifacts());
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        return new IvyDependencyMetadata(configuration, dependencyDescriptor, getReason(), endorse, getArtifacts());
    }

    @Override
    protected ModuleDependencyMetadata withRequested(ModuleComponentSelector newSelector) {
        IvyDependencyDescriptor newDescriptor = dependencyDescriptor.withRequested(newSelector);
        return new IvyDependencyMetadata(configuration, newDescriptor, getReason(), isEndorsingStrictVersions(), getArtifacts());
    }

    @Override
    protected ModuleDependencyMetadata withRequestedAndArtifacts(ModuleComponentSelector newSelector, List<IvyArtifactName> artifacts) {
        IvyDependencyDescriptor newDelegate = dependencyDescriptor.withRequested(newSelector);
        return new IvyDependencyMetadata(configuration, newDelegate, getReason(), isEndorsingStrictVersions(), artifacts);
    }

    public ModuleDependencyMetadata withDescriptor(IvyDependencyDescriptor descriptor) {
        return new IvyDependencyMetadata(configuration, descriptor, getReason(), isEndorsingStrictVersions(), dependencyDescriptor.getConfigurationArtifacts(configuration));
    }
}
