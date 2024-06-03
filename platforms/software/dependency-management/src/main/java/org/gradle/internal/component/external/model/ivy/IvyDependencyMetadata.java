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

import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ExternalModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
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
        return getDependencyDescriptor().selectLegacyConfigurations(configuration, targetComponentState, variantSelector.getFailureHandler());
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
