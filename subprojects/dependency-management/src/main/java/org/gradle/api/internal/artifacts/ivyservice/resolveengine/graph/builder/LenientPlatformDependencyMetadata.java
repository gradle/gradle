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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
        return this;
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return this;
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        return this;
    }

    @Override
    public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        if (targetComponent instanceof LenientPlatformResolveMetadata) {
            LenientPlatformResolveMetadata platformMetadata = (LenientPlatformResolveMetadata) targetComponent;
            return Collections.singletonList(new LenientPlatformConfigurationMetadata(platformMetadata.getPlatformState(), platformId));
        }
        // the target component exists, so we need to fallback to the traditional selection process
        return new LocalComponentDependencyMetadata(componentId, cs, null, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, null, Collections.emptyList(), Collections.emptyList(), false, false, true, false, false, null).selectConfigurations(consumerAttributes, targetComponent, consumerSchema, explicitRequestedCapabilities);
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
        return this;
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
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

    private boolean isExternalVariant() {
        return false;
    }

    @Override
    public String getReason() {
        return "belongs to platform " + platformId;
    }

    @Override
    public String toString() {
        return "virtual metadata for " + componentId;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        return new LenientPlatformDependencyMetadata(resolveState, from, cs, componentId, platformId, true, transitive);
    }

    private class LenientPlatformConfigurationMetadata extends DefaultConfigurationMetadata implements ConfigurationMetadata {

        private final VirtualPlatformState platformState;
        private final ComponentIdentifier platformId;

        public LenientPlatformConfigurationMetadata(VirtualPlatformState platform, @Nullable ComponentIdentifier platformId) {
            super(componentId, "default", true, false, ImmutableSet.of("default"), ImmutableList.of(), VariantMetadataRules.noOp(), ImmutableList.of(), ImmutableAttributes.EMPTY, false);
            this.platformState = platform;
            this.platformId = platformId;
        }

        @Override
        public List<? extends ModuleDependencyMetadata> getDependencies() {
            List<ModuleDependencyMetadata> result = null;
            List<String> candidateVersions = platformState.getCandidateVersions();
            Set<ModuleResolveState> modules = platformState.getParticipatingModules();
            for (ModuleResolveState module : modules) {
                ComponentState selected = module.getSelected();
                if (selected != null) {
                    String componentVersion = selected.getId().getVersion();
                    for (String target : candidateVersions) {
                        ModuleComponentIdentifier leafId = DefaultModuleComponentIdentifier.newId(module.getId(), target);
                        ModuleComponentSelector leafSelector = DefaultModuleComponentSelector.newSelector(module.getId(), target);
                        ComponentIdentifier platformId = platformState.getSelectedPlatformId();
                        if (platformId == null) {
                            // Not sure this can happen, unless in error state
                            platformId = this.platformId;
                        }
                        if (!componentVersion.equals(target)) {
                            // We will only add dependencies to the leaves if there is such a published module
                            PotentialEdge potentialEdge = PotentialEdge.of(resolveState, from, leafId, leafSelector, platformId, platformState.isForced(), false);
                            if (potentialEdge.metadata != null) {
                                result = registerPlatformEdge(result, modules, leafId, leafSelector, platformId, platformState.isForced());
                                break;
                            }
                        } else {
                            // at this point we know the component exists
                            result = registerPlatformEdge(result, modules, leafId, leafSelector, platformId, platformState.isForced());
                            break;
                        }
                    }
                    platformState.attachOrphanEdges();
                }
            }
            return result == null ? Collections.emptyList() : result;
        }

        private List<ModuleDependencyMetadata> registerPlatformEdge(@Nullable List<ModuleDependencyMetadata> result, Set<ModuleResolveState> modules, ModuleComponentIdentifier leafId, ModuleComponentSelector leafSelector, ComponentIdentifier platformId, boolean force) {
            if (result == null) {
                result = Lists.newArrayListWithExpectedSize(modules.size());
            }
            result.add(new LenientPlatformDependencyMetadata(
                resolveState,
                from,
                leafSelector,
                leafId,
                platformId,
                force,
                false
            ));
            return result;
        }
    }
}
