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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A {@link ModuleDependencyMetadata} implementation that is backed by an {@link ExternalDependencyDescriptor}.
 */
public abstract class ExternalModuleDependencyMetadata implements ModuleDependencyMetadata {
    private final String reason;
    private final boolean isEndorsing;
    private final List<IvyArtifactName> artifacts;

    public ExternalModuleDependencyMetadata(@Nullable String reason, boolean endorsing, List<IvyArtifactName> artifacts) {
        this.reason = reason;
        this.isEndorsing = endorsing;
        this.artifacts = artifacts;
    }

    public abstract ExternalDependencyDescriptor getDependencyDescriptor();

    /**
     * Choose a set of target configurations based on: a) the consumer attributes (with associated schema) and b) the target component.
     *
     * Use attribute matching to choose a single variant when the target component has variants,
     * otherwise revert to legacy selection of target configurations.
     */
    @Override
    public GraphVariantSelectionResult selectVariants(GraphVariantSelector variantSelector, ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        if (!targetComponentState.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching().isEmpty()) {
            VariantGraphResolveState selected = variantSelector.selectByAttributeMatching(
                consumerAttributes,
                explicitRequestedCapabilities,
                targetComponentState,
                consumerSchema,
                getArtifacts()
            );
            return new GraphVariantSelectionResult(Collections.singletonList(selected), true);
        }

        return selectLegacyConfigurations(variantSelector, consumerAttributes, targetComponentState, consumerSchema);
    }

    /**
     * Select target graph variants in an ecosystem-dependent manner.
     *
     * This method is called when the target component does not have variants to select from.
     */
    protected abstract GraphVariantSelectionResult selectLegacyConfigurations(
        GraphVariantSelector variantSelector,
        ImmutableAttributes consumerAttributes,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema
    );

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public abstract List<ExcludeMetadata> getExcludes();

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getModuleIdentifier(), moduleTarget.getVersionConstraint(), moduleTarget.getAttributes(), moduleTarget.getRequestedCapabilities());
            if (newSelector.equals(getSelector())) {
                return this;
            }
            return withRequested(newSelector);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getModuleIdentifier(), moduleTarget.getVersionConstraint(), moduleTarget.getAttributes(), moduleTarget.getRequestedCapabilities());
            if (newSelector.equals(getSelector()) && getArtifacts().equals(artifacts)) {
                return this;
            }
            return withRequestedAndArtifacts(newSelector, artifacts);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        ModuleComponentSelector selector = getSelector();
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getRequestedCapabilities());
        return withRequested(newSelector);
    }

    protected abstract ModuleDependencyMetadata withRequested(ModuleComponentSelector newSelector);

    protected abstract ModuleDependencyMetadata withRequestedAndArtifacts(ModuleComponentSelector newSelector, List<IvyArtifactName> artifacts);

    @Override
    public ModuleComponentSelector getSelector() {
        return getDependencyDescriptor().getSelector();
    }

    @Override
    public boolean isChanging() {
        return getDependencyDescriptor().isChanging();
    }

    @Override
    public boolean isTransitive() {
        return getDependencyDescriptor().isTransitive();
    }

    @Override
    public boolean isConstraint() {
        return getDependencyDescriptor().isConstraint();
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return isEndorsing;
    }

    @Nullable
    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return getDependencyDescriptor().toString();
    }
}
