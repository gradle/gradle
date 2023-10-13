/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultComponentGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LenientPlatformGraphResolveState extends DefaultComponentGraphResolveState<LenientPlatformResolveMetadata, LenientPlatformResolveMetadata> {
    private final ComponentIdGenerator componentIdGenerator;
    private final ResolveState resolveState;

    public static LenientPlatformGraphResolveState of(
        ComponentIdGenerator componentIdGenerator,
        ModuleComponentIdentifier moduleComponentIdentifier,
        ModuleVersionIdentifier moduleVersionIdentifier,
        VirtualPlatformState platformState,
        NodeState platformNode,
        ResolveState resolveState
    ) {
        LenientPlatformResolveMetadata metadata = new LenientPlatformResolveMetadata(moduleComponentIdentifier, moduleVersionIdentifier, platformState, platformNode, resolveState);
        return new LenientPlatformGraphResolveState(componentIdGenerator.nextComponentId(), metadata, componentIdGenerator, resolveState);
    }

    private LenientPlatformGraphResolveState(long instanceId, LenientPlatformResolveMetadata metadata, ComponentIdGenerator componentIdGenerator, ResolveState resolveState) {
        super(instanceId, metadata, metadata, resolveState.getAttributeDesugaring(), componentIdGenerator);
        this.componentIdGenerator = componentIdGenerator;
        this.resolveState = resolveState;
    }

    @Nullable
    @Override
    public ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return new LenientPlatformGraphResolveState(componentIdGenerator.nextComponentId(), getMetadata().withVersion(componentIdentifier, moduleVersionIdentifier), componentIdGenerator, resolveState);
    }

    /**
     * @param platformId The consuming platform.
     */
    public VariantGraphResolveState getDefaultVariant(NodeState from, @Nullable ComponentIdentifier platformId) {
        return newResolveStateFor(new LenientPlatformConfigurationMetadata(getMetadata().getPlatformState(), getMetadata().getId(), from, platformId));
    }

    private class LenientPlatformConfigurationMetadata extends DefaultConfigurationMetadata implements ConfigurationMetadata {
        private final VirtualPlatformState platformState;
        private final NodeState from;
        private final ComponentIdentifier platformId;

        public LenientPlatformConfigurationMetadata(VirtualPlatformState platform, ModuleComponentIdentifier componentId, NodeState from, @Nullable ComponentIdentifier platformId) {
            super(componentId, "default", true, false, ImmutableSet.of("default"), ImmutableList.of(), VariantMetadataRules.noOp(), ImmutableList.of(), ImmutableAttributes.EMPTY, false);
            this.platformState = platform;
            this.from = from;
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
                            if (potentialEdge.state != null) {
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
