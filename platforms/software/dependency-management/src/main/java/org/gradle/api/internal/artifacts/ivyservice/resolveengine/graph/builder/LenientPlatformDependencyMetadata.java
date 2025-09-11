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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

class LenientPlatformDependencyMetadata implements ModuleDependencyMetadata, ForcingDependencyMetadata {
    private final ResolveState resolveState;
    private final NodeState from;
    private final ModuleComponentSelector cs;
    private final ModuleComponentIdentifier componentId;
    private final ComponentIdentifier platformId; // just for reporting
    private final boolean force;
    private final boolean transitive;

    LenientPlatformDependencyMetadata(ResolveState resolveState, NodeState from, ModuleComponentSelector cs, ModuleComponentIdentifier componentId, @Nullable ComponentIdentifier platformId, boolean force, boolean transitive) {
        this.resolveState = resolveState;
        this.from = from;
        this.cs = cs;
        this.componentId = componentId;
        this.platformId = platformId;
        this.force = force;
        this.transitive = transitive;
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return cs;
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        throw new UnsupportedOperationException("Applying component metadata rules to lenient platform dependencies is not supported.");
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        throw new UnsupportedOperationException("Applying component metadata rules to lenient platform dependencies is not supported.");
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        throw new UnsupportedOperationException("Applying component metadata rules to lenient platform dependencies is not supported.");
    }

    @Override
    public @Nullable List<? extends VariantGraphResolveState> overrideVariantSelection(
        GraphVariantSelector variantSelector,
        ImmutableAttributes consumerAttributes,
        ComponentGraphResolveState targetComponentState,
        ImmutableAttributesSchema consumerSchema
    ) {
        if (targetComponentState instanceof LenientPlatformGraphResolveState) {
            LenientPlatformGraphResolveState lenientPlatform = (LenientPlatformGraphResolveState) targetComponentState;
            VariantGraphResolveState variant = lenientPlatform.getCandidatesForGraphVariantSelection().getVariantForSourceNode(from, platformId);
            return Collections.singletonList(variant);
        }

        return null;
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return Collections.emptyList();
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return Collections.emptyList();
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        // TODO: This gets called when performing substitutions.
        //       We probably shouldn't ignore this.
        return this;
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        // TODO: This gets called when performing substitutions.
        //       We probably shouldn't ignore this.
        return this;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isConstraint() {
        return true;
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return false;
    }

    @Override
    public String getReason() {
        return "belongs to platform " + platformId;
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        return new LenientPlatformDependencyMetadata(resolveState, from, cs, componentId, platformId, true, transitive);
    }
}
