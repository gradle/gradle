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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GradleDependencyMetadata implements ModuleDependencyMetadata, ForcingDependencyMetadata {
    private final ModuleComponentSelector selector;
    private final List<ExcludeMetadata> excludes;
    private final boolean constraint;
    private final boolean endorsing;
    private final String reason;
    private final boolean force;
    private final List<IvyArtifactName> artifacts;

    public GradleDependencyMetadata(ModuleComponentSelector selector, List<ExcludeMetadata> excludes, boolean constraint, boolean endorsing, @Nullable String reason, boolean force, @Nullable IvyArtifactName artifact) {
        this(selector, excludes, constraint, endorsing, reason, force, artifact == null ? ImmutableList.of() : ImmutableList.of(artifact));
    }

    private GradleDependencyMetadata(ModuleComponentSelector selector, List<ExcludeMetadata> excludes, boolean constraint, boolean endorsing, @Nullable String reason, boolean force, List<IvyArtifactName> artifacts) {
        this.selector = selector;
        this.excludes = excludes;
        this.reason = reason;
        this.constraint = constraint;
        this.endorsing = endorsing;
        this.force = force;
        this.artifacts = artifacts;
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Nullable
    public IvyArtifactName getDependencyArtifact() {
        return artifacts.isEmpty() ? null : artifacts.get(0);
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        return new GradleDependencyMetadata(DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getCapabilitySelectors()), excludes, constraint, endorsing, reason, force, artifacts);
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        if (Objects.equal(reason, this.reason)) {
            return this;
        }
        return new GradleDependencyMetadata(selector, excludes, constraint, endorsing, reason, force, artifacts);
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        if (endorse == this.endorsing) {
            return this;
        }
        return new GradleDependencyMetadata(selector, excludes, constraint, endorse, reason, force, artifacts);
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            return new GradleDependencyMetadata((ModuleComponentSelector) target, excludes, constraint, endorsing, reason, force, artifacts);
        }
        return new DefaultProjectDependencyMetadata((ProjectComponentSelector) target, this);
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (target instanceof ModuleComponentSelector) {
            return new GradleDependencyMetadata((ModuleComponentSelector) target, excludes, constraint, endorsing, reason, force, artifacts);
        }
        return new DefaultProjectDependencyMetadata((ProjectComponentSelector) target, this);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return excludes;
    }

    @Override
    public GraphVariantSelectionResult selectVariants(GraphVariantSelector variantSelector, ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, ImmutableAttributesSchema consumerSchema, Set<CapabilitySelector> explicitRequestedCapabilities) {
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

        // Fallback to legacy variant selection for target components that don't support attribute matching.
        VariantGraphResolveState selected = variantSelector.selectLegacyVariant(consumerAttributes, targetComponentState, consumerSchema, variantSelector.getFailureHandler());
        return new GraphVariantSelectionResult(Collections.singletonList(selected), false);
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public boolean isTransitive() {
        // Constraints are _never_ transitive
        return !isConstraint();
    }

    @Override
    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return endorsing;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "GradleDependencyMetadata: " + selector.toString();
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        return new GradleDependencyMetadata(selector, excludes, constraint, endorsing, reason, true, artifacts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradleDependencyMetadata that = (GradleDependencyMetadata) o;
        return constraint == that.constraint &&
            force == that.force &&
            Objects.equal(selector, that.selector) &&
            Objects.equal(excludes, that.excludes) &&
            Objects.equal(reason, that.reason) &&
            Objects.equal(artifacts, that.artifacts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(selector, excludes, constraint, reason, force, artifacts);
    }
}
