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
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.capabilities.CapabilityInternal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class DefaultCapabilitiesConflictHandler implements CapabilitiesConflictHandler {
    private final List<Resolver> resolvers;

    /**
     * Tracks conflicted nodes by the capability id.
     */
    private final Map<String, ConflictedNodesTracker> capabilityWithoutVersionToTracker = new HashMap<>();

    private final Deque<String> conflicts = new ArrayDeque<>();

    public DefaultCapabilitiesConflictHandler(List<Resolver> resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public PotentialConflict registerCandidate(CapabilitiesConflictHandler.Candidate candidate) {
        CapabilityInternal capability = candidate.getCapability();

        ConflictedNodesTracker tracker = capabilityWithoutVersionToTracker.computeIfAbsent(capability.getCapabilityId(), k -> new ConflictedNodesTracker(capability));
        // TODO: Is there a way to not do this filtering here?
        tracker.removeIf(n -> !n.isSelected());
        tracker.addAll(candidate.getImplicitCapabilityProviders());

        NodeState node = candidate.getNode();
        if (tracker.add(node) && tracker.hasConflictedNodes()) {
            // If the root node is in conflict, find its module ID
            ModuleIdentifier rootId = null;
            for (NodeState ns : tracker) {
                if (ns.isRoot()) {
                    rootId = ns.getComponent().getId().getModule();
                }
            }

            // TODO: Why do we need this copy? Why can't we update the nodes in the tracker?
            Set<NodeState> candidatesForConflict = tracker.getConflictedNodesCopy();
            if (rootId != null && candidatesForConflict.size() > 1) {
                // This is a special case for backwards compatibility: it is possible to have
                // a cycle where the root component depends on a library which transitively
                // depends on a different version of the root module. In this case, we effectively
                // allow 2 modules to have the same capability, so we filter the nodes coming
                // from transitive dependencies
                ModuleIdentifier rootModuleId = rootId;
                candidatesForConflict.removeIf(n -> !n.isRoot() && n.getComponent().getId().getModule().equals(rootModuleId));
            }

            // For a conflict we want at least 2 nodes, and at least one of them should not be rejected
            // TODO: Seems odd to filter for rejected nodes here
            if (candidatesForConflict.size() > 1 && !candidatesForConflict.stream().allMatch(n -> n.getComponent().isRejected())) {
                if (tracker.createOrUpdateConflict(candidatesForConflict)) {
                    conflicts.add(tracker.capabilityId);
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
        }
        return PotentialConflictFactory.noConflict();
    }

    @Override
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    @Override
    public void resolveNextConflict(Action<ConflictResolutionResult> resolutionAction) {
        String capabilityInConflict = conflicts.remove();

        ConflictedNodesTracker conflictTracker = capabilityWithoutVersionToTracker.get(capabilityInConflict);
        CapabilityConflict conflict = conflictTracker.updateClearAndReturnConflict();

        if (!conflict.isValidConflict()) {
            return;
        }

        // TODO: What if, after we filter, we only have nodes with the default capability?
        // We should treat this conflict as a version conflict.

        Details details = new Details(conflict);
        for (Resolver resolver : resolvers) {
            resolver.resolve(details);
            if (details.hasResult()) {
                resolutionAction.execute(details);

                if (conflict.nodes.size() > 1) {
                    assert details.reason != null;
                    details.getSelected().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(details.reason));
                }

                return;
            }
        }

        // Otherwise, reject all remaining candidates.
        Collection<? extends Capability> capabilityVersions = ((ResolutionDetails) details).getCapabilityVersions();
        for (Capability capabilityVersion : capabilityVersions) {
            Collection<? extends CandidateDetails> candidates = ((ResolutionDetails) details).getCandidates(capabilityVersion);
            if (!candidates.isEmpty()) {
                // Arbitrarily select and mark all as rejected
                for (CandidateDetails candidate : candidates) {
                    candidate.reject();
                }
            }
        }
    }

    @Override
    public boolean hasSeenNonDefaultCapabilityExplicitly(CapabilityInternal capability) {
        return capabilityWithoutVersionToTracker.containsKey(capability.getCapabilityId());
    }

    public static CapabilitiesConflictHandler.Candidate candidate(NodeState node, CapabilityInternal capability, Collection<NodeState> implicitCapabilityProviders) {
        return new Candidate(node, capability, implicitCapabilityProviders);
    }

    private static class Candidate implements CapabilitiesConflictHandler.Candidate {
        private final NodeState node;
        private final CapabilityInternal capability;
        private final Collection<NodeState> implicitCapabilityProviders;

        public Candidate(NodeState node, CapabilityInternal capability, Collection<NodeState> implicitCapabilityProviders) {
            this.node = node;
            this.capability = capability;
            this.implicitCapabilityProviders = implicitCapabilityProviders;
        }

        @Override
        public NodeState getNode() {
            return node;
        }

        @Override
        public CapabilityInternal getCapability() {
            return capability;
        }

        @Override
        public Collection<NodeState> getImplicitCapabilityProviders() {
            return implicitCapabilityProviders;
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
            List<NodeState> conflictedNodes = new ArrayList<>(nodes);
            conflictedNodes.remove(node);
            return conflictedNodes;
        }

        @Override
        public void withParticipatingModules(Action<? super ModuleIdentifier> action, ConflictResolutionResult result) {
            Set<ModuleIdentifier> seen = new HashSet<>();

            // Visit the winning module first so that when we visit unattached dependencies of
            // losing modules, the winning module always has a selected component.
            ModuleIdentifier winningModule = result.getSelected().getModule().getId();
            action.execute(winningModule);
            seen.add(winningModule);

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

        private final String group;
        private final String name;

        private final Set<NodeState> nodes;
        private final Set<Capability> descriptors;
        private final Map<NodeState, Set<NodeState>> nodeToDependentNodes;

        private CapabilityConflict(String group, String name, Set<NodeState> nodes, boolean alreadySeen) {
            this(group, name, nodes, alreadySeen ? buildDependentRelationships(nodes) : emptyMap());
        }

        private CapabilityConflict(String group, String name, Set<NodeState> nodes, Map<NodeState, Set<NodeState>> nodeToDependentNodes) {
            this.group = group;
            this.name = name;
            this.nodes = nodes;
            this.nodeToDependentNodes = nodeToDependentNodes;
            final ImmutableSet.Builder<Capability> builder = new ImmutableSet.Builder<>();
            for (final NodeState node : nodes) {
                Capability capability = node.findCapability(group, name);
                if (capability != null) {
                    builder.add(capability);
                }
            }
            this.descriptors = builder.build();
        }

        private CapabilityConflict withDifferentNodes(Set<NodeState> selectedNodes) {
            return new CapabilityConflict(group, name, selectedNodes, nodeToDependentNodes);
        }

        /**
         * Validates that the conflict has at least one node that is not rejected.
         *
         * @return {@code true} if the conflict is valid
         */
        private boolean isValidConflict() {
            return !nodes.isEmpty() && nodes.stream().anyMatch(node -> !node.getComponent().isRejected());
        }

        private static Map<NodeState, Set<NodeState>> buildDependentRelationships(Set<NodeState> nodes) {
            HashMap<NodeState, Set<NodeState>> nodeToDependents = new HashMap<>();
            for (NodeState node : nodes) {
                Set<NodeState> reachableNodes = node.getReachableNodes();
                for (NodeState possibleDependency : nodes) {
                    if (node == possibleDependency) {
                        continue;
                    }
                    if (reachableNodes.contains(possibleDependency)) {
                        nodeToDependents.computeIfAbsent(possibleDependency, k -> new HashSet<>()).add(node);
                    }
                }
            }
            return nodeToDependents;
        }
    }

    /**
     * Wrapper object tracking recorded conflict participants.
     *
     * It also keeps track of the history of conflicts that were processed.
     */
    private static class ConflictedNodesTracker implements Iterable<NodeState> {
        private final String group;
        private final String name;
        private final String capabilityId;
        private final List<Set<NodeState>> previousConflictedNodes = new ArrayList<>();

        private Set<NodeState> currentConflictedNodes = new LinkedHashSet<>();

        /**
         * If non-null, the capability tracked by this tracker has a pending conflict
         * that must be resolved.
         */
        private CapabilityConflict pendingConflict;

        private ConflictedNodesTracker(CapabilityInternal capability) {
            this.group = capability.getGroup();
            this.name = capability.getName();
            this.capabilityId = capability.getCapabilityId();
        }

        /**
         * Updates, clears and returns the current conflict.
         *
         * If we encounter a conflict with deselected nodes, it is possible that the deselection was
         * caused by the conflict deselection (it can happen for other reasons too)
         * Record this fact, so that later if we see the conflict again, we can compute node relationships
         * between conflict participants.
         */
        private CapabilityConflict updateClearAndReturnConflict() {
            CapabilityConflict currentConflict = pendingConflict;
            this.pendingConflict = null;

            Set<NodeState> selectedNodes = new LinkedHashSet<>();
            boolean didFilter = false;
            for (NodeState node : currentConflict.nodes) {
                if (node.isSelected() || (!currentConflict.nodeToDependentNodes.isEmpty() && currentConflict.nodeToDependentNodes.getOrDefault(node, emptySet()).stream().anyMatch(NodeState::isSelected))) {
                    selectedNodes.add(node);
                } else {
                    didFilter = true;
                }
            }

            if (didFilter) {
                previousConflictedNodes.add(currentConflictedNodes);
                currentConflictedNodes = selectedNodes;
                return currentConflict.withDifferentNodes(selectedNodes);
            }

            return currentConflict;
        }

        private boolean removeIf(Predicate<? super NodeState> pre) {
            return currentConflictedNodes.removeIf(pre);
        }

        private void addAll(Collection<NodeState> extraNodes) {
            currentConflictedNodes.addAll(extraNodes);
        }

        private boolean add(NodeState node) {
            return currentConflictedNodes.add(node);
        }

        @Override
        public Iterator<NodeState> iterator() {
            return currentConflictedNodes.iterator();
        }

        private boolean hasConflictedNodes() {
            return currentConflictedNodes.size() > 1;
        }

        private Set<NodeState> getConflictedNodesCopy() {
            return new LinkedHashSet<>(currentConflictedNodes);
        }

        /**
         * Creates a new conflict object and replace pre-existing conflict with it.
         *
         * If we saw the conflict before, record relationship between nodes
         *
         * @param candidatesForConflict the conflict candidates
         *
         * @return true if this is the first time this tracker sees this conflict and
         *         if a conflict on this capability will need to be resolved.
         */
        private boolean createOrUpdateConflict(Set<NodeState> candidatesForConflict) {
            boolean newConflict = pendingConflict == null;
            this.pendingConflict = new CapabilityConflict(group, name, candidatesForConflict, previousConflictedNodes.contains(candidatesForConflict));
            return newConflict;
        }
    }
}
