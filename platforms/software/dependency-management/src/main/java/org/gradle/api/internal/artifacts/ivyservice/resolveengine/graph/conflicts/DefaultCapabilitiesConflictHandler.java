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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    private final CapabilityConflictResolver resolver;
    private final ResolveState resolveState;

    /**
     * Tracks conflicted nodes by the capability id.
     */
    private final Map<String, ConflictedNodesTracker> capabilityWithoutVersionToTracker = new HashMap<>();

    private final Deque<String> conflicts = new ArrayDeque<>();

    public DefaultCapabilitiesConflictHandler(ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules, ResolveState resolveState) {
        this.resolver = new CapabilityConflictResolver(rules);
        this.resolveState = resolveState;
    }

    @Override
    public boolean registerCandidate(final NodeState node) {
        ImmutableCapabilities capabilities = node.getMetadata().getCapabilities();
        if (capabilities.isEmpty()) {
            // If there's more than one node selected for the same component, we need to add
            // the implicit capability to the list, in order to make sure we can discover conflicts
            // between variants of the same module.
            // We also need to add the implicit capability if it was seen before as an explicit
            // capability in order to detect the conflict between the two.
            // Note that the fact that the implicit capability is not included in other cases
            // is not a bug but a performance optimization.
            if (node.getComponent().hasMoreThanOneSelectedNodeUsingVariantAwareResolution() ||
                hasSeenNonDefaultCapabilityExplicitly(node.getComponent().getImplicitCapability())
            ) {
                CapabilityInternal capability = node.getComponent().getImplicitCapability();
                return registerCapability(node, capability);
            }

            return false;
        } else {
            boolean defaultCapabilityHasConflict = hasSeenNonDefaultCapabilityExplicitly(node.getComponent().getImplicitCapability());

            boolean foundConflict = false;
            for (CapabilityInternal capability : capabilities) {
                // Only process non-default capabilities
                // Or, for the default capability if we have seen that capability on a node for which it is not the default
                // Or, the component has multiple selected variants, in which case two nodes in that component may conflict with each other
                if (!capability.equals(node.getComponent().getImplicitCapability()) || defaultCapabilityHasConflict || node.getComponent().hasMoreThanOneSelectedNodeUsingVariantAwareResolution()) {
                    foundConflict |= registerCapability(node, capability);
                }
            }

            return foundConflict;
        }
    }

    private boolean registerCapability(NodeState node, CapabilityInternal capability) {
        ConflictedNodesTracker tracker = capabilityWithoutVersionToTracker.computeIfAbsent(capability.getCapabilityId(), k -> new ConflictedNodesTracker(capability));
        // TODO: Is there a way to not do this filtering here?
        tracker.removeIf(n -> !n.isSelected());

        // This is a performance optimization. Most modules do not declare capabilities. So, instead of systematically registering
        // an implicit capability for each module that we see, we only consider modules which _declare_ capabilities. If they do,
        // then we try to find a module which provides the same capability. If that module has been found, then we register it.
        // Otherwise, we have nothing to do. This avoids most registrations.
        ModuleResolveState module = resolveState.findModule(DefaultModuleIdentifier.newId(capability.getGroup(), capability.getName()));
        if (module != null) {
            for (ComponentState version : module.getVersions()) {
                for (NodeState potentialNode : version.getNodes()) {
                    // Collect nodes as implicit capability providers if different from current node, selected and not having explicit capabilities
                    if (node != potentialNode && potentialNode.isSelected() && potentialNode.getMetadata().getCapabilities().isEmpty()) {
                        tracker.add(potentialNode);
                    }
                }
            }
        }

        if (tracker.add(node) && tracker.hasConflictedNodes()) {
            // If the root node is in conflict, find its module ID
            ModuleIdentifier rootId = null;
            for (NodeState ns : tracker) {
                if (ns.isRoot()) {
                    rootId = ns.getComponent().getModule().getId();
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
                candidatesForConflict.removeIf(n -> !n.isRoot() && n.getComponent().getModule().getId().equals(rootModuleId));
            }

            // For a conflict we want at least 2 nodes, and at least one of them should not be rejected
            // TODO: Seems odd to filter for rejected nodes here
            if (candidatesForConflict.size() > 1 && !candidatesForConflict.stream().allMatch(n -> n.getComponent().isRejected())) {
                if (tracker.createOrUpdateConflict(candidatesForConflict)) {
                    conflicts.add(tracker.capabilityId);
                }

                for (NodeState candidateNode : candidatesForConflict) {
                    candidateNode.getComponent().getModule().clearSelection();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    @Override
    public void resolveNextConflict() {
        String capabilityInConflict = conflicts.remove();

        ConflictedNodesTracker conflictTracker = capabilityWithoutVersionToTracker.get(capabilityInConflict);
        CapabilityConflict conflict = conflictTracker.updateClearAndReturnConflict();

        if (!conflict.isValidConflict()) {
            return;
        }

        // TODO: What if, after we filter, we only have nodes with the default capability?
        // We should treat this conflict as a version conflict.

        resolver.resolve(conflict.group, conflict.name, conflict.nodes);
    }

    /**
     * Has the given capability been seen as a non-default capability on a node?
     * This is needed to determine if default capabilities need to enter conflict detection.
     */
    private boolean hasSeenNonDefaultCapabilityExplicitly(CapabilityInternal capability) {
        return capabilityWithoutVersionToTracker.containsKey(capability.getCapabilityId());
    }

    private static class CapabilityConflict {

        private final String group;
        private final String name;
        private final Set<NodeState> nodes;
        private final Map<NodeState, Set<NodeState>> nodeToDependentNodes;

        private CapabilityConflict(String group, String name, Set<NodeState> nodes, boolean alreadySeen) {
            this(group, name, nodes, alreadySeen ? buildDependentRelationships(nodes) : emptyMap());
        }

        private CapabilityConflict(String group, String name, Set<NodeState> nodes, Map<NodeState, Set<NodeState>> nodeToDependentNodes) {
            this.group = group;
            this.name = name;
            this.nodes = nodes;
            this.nodeToDependentNodes = nodeToDependentNodes;
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
