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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultCapabilitiesConflictHandler implements CapabilitiesConflictHandler {
    private final List<Resolver> resolvers;

    private final Deque<String> conflicts = new ArrayDeque<>();
    private final Map<String, CapabilityConflict> capabilityIdToConflict = new HashMap<>();
    private final Map<NodeState, Set<String>> nodesToConflicts = new HashMap<>();

    public DefaultCapabilitiesConflictHandler(List<Resolver> resolvers) {
        this.resolvers = resolvers;
    }

    private final Map<String, Set<NodeState>> implicitCapabilityProviders = new HashMap<>();
    private final Map<String, Set<NodeState>> explicitCapabilityProviders = new HashMap<>();

    @Override
    public List<PotentialConflict> registerNode(NodeState node) {
        if (node.isRoot() && node.getIncomingEdges().isEmpty()) {
            // The root node does not participate in capability conflict resolution as the root node
            // cannot contribute artifacts. However, if the root node has incoming edges, it is treated
            // as a non-root node (this is deprecated). In this case, we should register the root node
            // as providing capabilities.
            return Collections.emptyList();
        }

        ImmutableCapabilities capabilities = node.getMetadata().getCapabilities();
        if (capabilities.isEmpty()) {
            return registerNodeWithImplicitCapabilities(node);
        } else {
            return registerNodeWithExplicitCapabilities(node, capabilities);
        }
    }

    private List<PotentialConflict> registerNodeWithImplicitCapabilities(NodeState node) {
        CapabilityInternal implicitCapability = node.getComponent().getImplicitCapability();
        Set<NodeState> implicitProvidersForCapability = implicitCapabilityProviders.computeIfAbsent(implicitCapability.getCapabilityId(), k -> new LinkedHashSet<>(1));

        if (implicitProvidersForCapability.add(node)) {
            // We can have a conflict on implicit capabilities if:
            // 1. We've seen this capability explicitly from another node.
            //    Otherwise, all conflicting nodes are from the same module and will be resolved by version conflict resolution.
            // 2. We have multiple nodes from the same component.
            //    All nodes are from the same module, but can be selected in the same graph since they're from the same component.
            Set<NodeState> explicitProvidersForCapability = explicitCapabilityProviders.get(implicitCapability.getCapabilityId());
            if ((explicitProvidersForCapability != null && !explicitProvidersForCapability.isEmpty()) ||
                hasImplicitConflictBetweenNodesInSameComponent(implicitProvidersForCapability)
            ) {
                Set<NodeState> candidates = merge(implicitProvidersForCapability, explicitProvidersForCapability);
                return Collections.singletonList(createConflict(implicitCapability, candidates));
            }
        }

        return Collections.emptyList();
    }

    private static boolean hasImplicitConflictBetweenNodesInSameComponent(Set<NodeState> implicitProvidersForCapability) {
        if (implicitProvidersForCapability.size() < 2) {
            return false;
        }
        Set<ComponentState> seen = new HashSet<>(implicitProvidersForCapability.size());
        for (NodeState node : implicitProvidersForCapability) {
            // Nodes that are not selected by variant-aware resolution do not participate in capability conflict resolution.
            // This is mostly due to ivy variants not having well-defined capabilities by default, and they are traditionally
            // not selected by variant-aware resolution. This is a heuristic, and we should make this better.
            if (node.isSelectedByVariantAwareResolution() && !seen.add(node.getComponent())) {
                return true;
            }
        }
        return false;
    }

    private List<PotentialConflict> registerNodeWithExplicitCapabilities(NodeState node, ImmutableCapabilities capabilities) {
        List<PotentialConflict> conflicts = Collections.emptyList();
        for (CapabilityInternal capability : capabilities) {
            Set<NodeState> explicitCapabilityProviders = this.explicitCapabilityProviders.computeIfAbsent(capability.getCapabilityId(), k -> new LinkedHashSet<>(1));

            if (explicitCapabilityProviders.add(node)) {
                Set<NodeState> implicitCapabilityProviders = this.implicitCapabilityProviders.get(capability.getCapabilityId());

                if (explicitCapabilityProviders.size() > 1 ||
                    (implicitCapabilityProviders != null && !implicitCapabilityProviders.isEmpty())
                ) {
                    Set<NodeState> candidates = merge(implicitCapabilityProviders, explicitCapabilityProviders);
                    if (conflicts.isEmpty()) {
                        conflicts = new ArrayList<>();
                    }
                    conflicts.add(createConflict(capability, candidates));
                }
            }
        }

        return conflicts;
    }

    private static Set<NodeState> merge(
        @Nullable Set<NodeState> implicitProvidersForCapability,
        @Nullable Set<NodeState> explicitProvidersForCapability
    ) {
        assert implicitProvidersForCapability != null || explicitProvidersForCapability != null;
        if (implicitProvidersForCapability == null) {
            return new LinkedHashSet<>(explicitProvidersForCapability);
        } else if (explicitProvidersForCapability == null) {
            return new LinkedHashSet<>(implicitProvidersForCapability);
        }

        Set<NodeState> candidates = new LinkedHashSet<>(implicitProvidersForCapability.size() + explicitProvidersForCapability.size());
        candidates.addAll(implicitProvidersForCapability);
        candidates.addAll(explicitProvidersForCapability);
        return candidates;
    }

    private PotentialConflict createConflict(
        CapabilityInternal capability,
        Set<NodeState> candidatesForConflict
    ) {
        CapabilityConflict conflict = new CapabilityConflict(capability.getGroup(), capability.getName(), candidatesForConflict);
        if (capabilityIdToConflict.put(capability.getCapabilityId(), conflict) == null) {
            // No previous conflict, enqueue the capability for resolution
            conflicts.add(capability.getCapabilityId());
        }

        for (NodeState node : candidatesForConflict) {
            Set<String> conflicts = nodesToConflicts.computeIfAbsent(node, k -> new HashSet<>());
            conflicts.add(capability.getCapabilityId());
        }

        return new PotentialConflict() {
            @Override
            public void withParticipatingModules(Action<ModuleIdentifier> action) {
                for (NodeState node : candidatesForConflict) {
                    action.execute(node.getComponent().getId().getModule());
                }
            }

            @Override
            public boolean conflictExists() {
                return true;
            }
        };
    }

    @Override
    public void unregisterNode(NodeState node) {
        if (node.getMetadata().getCapabilities().isEmpty()) {
            CapabilityInternal implicitCapability = node.getComponent().getImplicitCapability();

            Set<NodeState> providersForCapability = implicitCapabilityProviders.get(implicitCapability.getCapabilityId());
            if (providersForCapability != null) {
                providersForCapability.remove(node);
            }
        } else {
            for (CapabilityInternal capability : node.getMetadata().getCapabilities()) {
                Set<NodeState> providersForCapability = explicitCapabilityProviders.get(capability.getCapabilityId());
                if (providersForCapability != null) {
                    providersForCapability.remove(node);
                }
            }
        }

        // If this node participated in a conflict, remove it from that conflict.
        Set<String> conflictsForNode = nodesToConflicts.remove(node);
        if (conflictsForNode != null) {
            for (String conflictId : conflictsForNode) {
                CapabilityConflict conflict = capabilityIdToConflict.get(conflictId);
                conflict.nodes.remove(node);
                // TODO: What if, after we remove, we only have nodes with the implicit capability?
                // We should treat this conflict as a version conflict.
                if (conflict.nodes.isEmpty()) {
                    conflicts.remove(conflictId);
                    capabilityIdToConflict.remove(conflictId);
                }
            }
        }
    }

    @Override
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    @Override
    public void resolveNextConflict(Action<ConflictResolutionResult> resolutionAction) {
        String capabilityInConflict = conflicts.remove();
        CapabilityConflict conflict = capabilityIdToConflict.remove(capabilityInConflict);

        Details details = new Details(conflict);
        for (Resolver resolver : resolvers) {
            resolver.resolve(details);
            if (details.hasResult()) {
                resolutionAction.execute(details);

                if (conflict.nodes.size() > 1) {
                    assert details.reason != null;
                    details.getSelected().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(details.reason));
                }

                for (NodeState node : conflict.nodes) {
                    Set<String> conflictsForNode = nodesToConflicts.get(node);
                    conflictsForNode.remove(capabilityInConflict);
                    if (conflictsForNode.isEmpty()) {
                        nodesToConflicts.remove(node);
                    }
                }

                return;
            }
        }
    }

    private static class Details implements ResolutionDetails {
        private final CapabilityConflict conflict;
        private final Set<NodeState> evicted = new HashSet<>();
        private NodeState selected;
        private Describable reason;

        private Details(CapabilityConflict conflict) {
            this.conflict = conflict;
        }

        @Override
        public Collection<? extends Capability> getCapabilityVersions() {
            return conflict.descriptors;
        }

        @Override
        public Collection<? extends CandidateDetails> getCandidates(Capability capability) {
            ImmutableList.Builder<CandidateDetails> candidates = new ImmutableList.Builder<>();
            String group = capability.getGroup();
            String name = capability.getName();
            String version = capability.getVersion();
            for (final NodeState node : conflict.nodes) {
                if (!evicted.contains(node)) {
                    Capability componentCapability = node.findCapability(group, name);
                    if (componentCapability != null && componentCapability.getVersion().equals(version)) {
                        candidates.add(new CandidateDetails() {
                            @Override
                            public ComponentIdentifier getId() {
                                return node.getComponent().getComponentId();
                            }

                            @Override
                            public String getVariantName() {
                                return node.getMetadata().getName();
                            }

                            @Override
                            public void evict() {
                                node.evict();
                                evicted.add(node);
                            }

                            @Override
                            public void select() {
                                selected = node;
                            }

                            @Override
                            public void reject() {
                                ComponentState component = node.getComponent();
                                component.rejectForCapabilityConflict(capability, conflictedNodes(node, conflict.nodes));
                                component.selectAndRestartModule();
                            }

                            @Override
                            public void byReason(Describable description) {
                                reason = description;
                            }
                        });
                    }
                }
            }
            return candidates.build();
        }

        private Collection<NodeState> conflictedNodes(NodeState node, Collection<NodeState> nodes) {
            List<NodeState> conflictedNodes = Lists.newArrayList(nodes);
            conflictedNodes.remove(node);
            return conflictedNodes;
        }

        @Override
        public void withParticipatingModules(Action<? super ModuleIdentifier> action) {
            Set<ModuleIdentifier> seen = new HashSet<>();
            for (NodeState node : conflict.nodes) {
                ModuleIdentifier module = node.getComponent().getId().getModule();
                if (seen.add(module)) {
                    action.execute(module);
                }
            }
        }

        @Override
        public boolean hasResult() {
            return selected != null;
        }

        @Override
        public ComponentState getSelected() {
            return selected.getComponent();
        }
    }

    private static class CapabilityConflict {
        private final Set<NodeState> nodes;
        private final Set<Capability> descriptors;

        private CapabilityConflict(String group, String name, Set<NodeState> nodes) {
            this.nodes = nodes;
            final ImmutableSet.Builder<Capability> builder = new ImmutableSet.Builder<>();
            for (NodeState node : nodes) {
                Capability capability = node.findCapability(group, name);
                if (capability != null) {
                    builder.add(capability);
                }
            }
            this.descriptors = builder.build();
        }
    }

}
