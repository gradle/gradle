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

package org.gradle.internal.component.external.model.maven;

import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ExternalModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Represents a dependency declared in a Maven POM file.
 */
public class MavenDependencyMetadata extends ExternalModuleDependencyMetadata {

    private final MavenDependencyDescriptor dependencyDescriptor;

    public MavenDependencyMetadata(MavenDependencyDescriptor dependencyDescriptor) {
        this(dependencyDescriptor, null, false);
    }

    public MavenDependencyMetadata(MavenDependencyDescriptor dependencyDescriptor, @Nullable String reason, boolean endorsing) {
        this(dependencyDescriptor, reason, endorsing, dependencyDescriptor.getConfigurationArtifacts());
    }

    private MavenDependencyMetadata(MavenDependencyDescriptor dependencyDescriptor, @Nullable String reason, boolean endorsing, List<IvyArtifactName> artifacts) {
        super(reason, endorsing, artifacts);
        this.dependencyDescriptor = dependencyDescriptor;
    }

    @Override
    public MavenDependencyDescriptor getDependencyDescriptor() {
        return dependencyDescriptor;
    }

    @Override
    protected GraphVariantSelectionResult selectLegacyConfigurations(
        GraphVariantSelector variantSelector,
        ImmutableAttributes consumerAttributes,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema
    ) {
        VariantGraphResolveState selected = variantSelector.selectLegacyVariant(consumerAttributes, targetComponentState, consumerSchema, variantSelector.getFailureHandler());
        return new GraphVariantSelectionResult(Collections.singletonList(selected), false);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return getDependencyDescriptor().getConfigurationExcludes();
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return new MavenDependencyMetadata(dependencyDescriptor, reason, isEndorsingStrictVersions(), getArtifacts());
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        return new MavenDependencyMetadata(dependencyDescriptor, getReason(), endorse, getArtifacts());
    }

    @Override
    protected ModuleDependencyMetadata withRequested(ModuleComponentSelector newSelector) {
        MavenDependencyDescriptor newDescriptor = dependencyDescriptor.withRequested(newSelector);
        return new MavenDependencyMetadata(newDescriptor, getReason(), isEndorsingStrictVersions(), getArtifacts());
    }

    @Override
    protected ModuleDependencyMetadata withRequestedAndArtifacts(ModuleComponentSelector newSelector, List<IvyArtifactName> artifacts) {
        MavenDependencyDescriptor newDelegate = dependencyDescriptor.withRequested(newSelector);
        return new MavenDependencyMetadata(newDelegate, getReason(), isEndorsingStrictVersions(), artifacts);
    }
}
