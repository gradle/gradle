/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Information about a locally resolved dependency.
 */
public class LocalComponentDependencyMetadata implements LocalOriginDependencyMetadata {
    private final ComponentSelector selector;
    private final String dependencyConfiguration;
    private final List<ExcludeMetadata> excludes;
    private final List<IvyArtifactName> artifactNames;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;
    private final boolean constraint;
    private final boolean endorsing;
    private final boolean fromLock;
    private final String reason;

    public LocalComponentDependencyMetadata(
        ComponentSelector selector,
        @Nullable String dependencyConfiguration,
        List<IvyArtifactName> artifactNames,
        List<ExcludeMetadata> excludes,
        boolean force, boolean changing, boolean transitive, boolean constraint, boolean endorsing,
        @Nullable String reason
    ) {
        this(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, false, reason);
    }

    public LocalComponentDependencyMetadata(
        ComponentSelector selector,
        @Nullable String dependencyConfiguration,
        List<IvyArtifactName> artifactNames,
        List<ExcludeMetadata> excludes,
        boolean force, boolean changing, boolean transitive,
        boolean constraint, boolean endorsing, boolean fromLock,
        @Nullable String reason
    ) {
        this.selector = selector;
        this.dependencyConfiguration = dependencyConfiguration;
        this.artifactNames = asImmutable(artifactNames);
        this.excludes = excludes;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
        this.constraint = constraint;
        this.endorsing = endorsing;
        this.fromLock = fromLock;
        this.reason = reason;
    }

    private static List<IvyArtifactName> asImmutable(List<IvyArtifactName> artifactNames) {
        return artifactNames.isEmpty() ? Collections.emptyList() : artifactNames instanceof ImmutableList ? artifactNames : ImmutableList.copyOf(artifactNames);
    }

    @Override
    public String toString() {
        return "dependency: " + selector + " to-conf: " + dependencyConfiguration;
    }

    @Override
    public ComponentSelector getSelector() {
        return selector;
    }

    @Override
    public GraphVariantSelectionResult selectVariants(
        GraphVariantSelector variantSelector,
        ImmutableAttributes consumerAttributes,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema,
        Collection<? extends Capability> explicitRequestedCapabilities
    ) {
        // If a specific variant is requested by name, select it.
        if (dependencyConfiguration != null) {
            VariantGraphResolveState selected = variantSelector.selectVariantByConfigurationName(dependencyConfiguration, consumerAttributes, targetComponentState, consumerSchema);
            return new GraphVariantSelectionResult(Collections.singletonList(selected), false);
        }

        // Use attribute matching if it is supported.
        if (!targetComponentState.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching().isEmpty()) {
            VariantGraphResolveState selected = variantSelector.selectByAttributeMatching(
                consumerAttributes,
                explicitRequestedCapabilities, targetComponentState,
                consumerSchema,
                getArtifacts()
            );
            return new GraphVariantSelectionResult(Collections.singletonList(selected), true);
        }

        // Otherwise, select the legacy configuration.
        VariantGraphResolveState selected = variantSelector.selectLegacyVariant(consumerAttributes, targetComponentState, consumerSchema, variantSelector.getFailureHandler());
        return new GraphVariantSelectionResult(Collections.singletonList(selected), false);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return excludes;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isForce() {
        return force;
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
    public List<IvyArtifactName> getArtifacts() {
        return artifactNames;
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        if (selector.equals(target)) {
            return this;
        }
        return copyWithTarget(target);
    }

    @Override
    public LocalOriginDependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (selector.equals(target) && artifacts.equals(getArtifacts())) {
            return this;
        }
        return copyWithTargetAndArtifacts(target, artifacts);
    }

    @Override
    public LocalOriginDependencyMetadata forced() {
        if (force) {
            return this;
        }
        return copyWithForce();
    }

    @Override
    public boolean isFromLock() {
        return fromLock;
    }

    @Override
    public DependencyMetadata withReason(String reason) {
        if (Objects.equal(reason, this.reason)) {
            return this;
        }
        return copyWithReason(reason);
    }

    private LocalOriginDependencyMetadata copyWithTarget(ComponentSelector selector) {
        return new LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason);
    }

    private LocalOriginDependencyMetadata copyWithTargetAndArtifacts(ComponentSelector selector, List<IvyArtifactName> artifactNames) {
        return new LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason);
    }

    private LocalOriginDependencyMetadata copyWithReason(String reason) {
        return new LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason);
    }

    private LocalOriginDependencyMetadata copyWithForce() {
        return new LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, true, changing, transitive, constraint, endorsing, fromLock, reason);
    }

}
