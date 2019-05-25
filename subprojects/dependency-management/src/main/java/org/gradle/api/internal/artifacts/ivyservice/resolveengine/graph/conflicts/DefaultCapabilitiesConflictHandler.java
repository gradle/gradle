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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.CapabilityInternal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DefaultCapabilitiesConflictHandler implements CapabilitiesConflictHandler {
    private final List<Resolver> resolvers = Lists.newArrayListWithExpectedSize(3);
    private final Map<String, Set<NodeState>> capabilityWithoutVersionToNodes = Maps.newHashMap();
    private final Deque<CapabilityConflict> conflicts = new ArrayDeque<CapabilityConflict>();

    @Override
    public PotentialConflict registerCandidate(CapabilitiesConflictHandler.Candidate candidate) {
        CapabilityInternal capability = (CapabilityInternal) candidate.getCapability();
        String group = capability.getGroup();
        String name = capability.getName();
        final Set<NodeState> nodes = findNodesFor(capability);
        Collection<NodeState> implicitCapabilityProviders = candidate.getImplicitCapabilityProviders();
        nodes.addAll(implicitCapabilityProviders);
        NodeState node = candidate.getNode();
        if ((nodes.add(node) || implicitCapabilityProviders.contains(node)) && nodes.size() > 1) {
            // The registered nodes may contain nodes which are no longer selected.
            // We don't remove them from the list in the first place because it proved to be
            // slower than filtering as needed.
            ModuleIdentifier rootId = null;
            final List<NodeState> candidatesForConflict = Lists.newArrayListWithCapacity(nodes.size());
            for (NodeState ns : nodes) {
                // TODO: CC the special casing of virtual platform should go away if we can implement
                // disambiguation of variants for a _single_ component
                if (ns.isSelected() && !ns.isAttachedToVirtualPlatform()) {
                    candidatesForConflict.add(ns);
                    if (ns.isRoot()) {
                        rootId = ns.getComponent().getId().getModule();
                    }
                }
            }
            if (rootId != null && candidatesForConflict.size() > 1) {
                // This is a special case for backwards compatibility: it is possible to have
                // a cycle where the root component depends on a library which transitively
                // depends on a different version of the root module. In this case, we effectively
                // allow 2 modules to have the same capability, so we filter the nodes coming
                // from transitive dependencies
                ModuleIdentifier rootModuleId = rootId;
                candidatesForConflict.removeIf(n -> !n.isRoot() && n.getComponent().getId().getModule().equals(rootModuleId));
            }
            if (candidatesForConflict.size() > 1) {
                PotentialConflict conflict = new PotentialConflict() {
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
                conflicts.add(new CapabilityConflict(group, name, candidatesForConflict));
                return conflict;
            }
        }
        return PotentialConflictFactory.noConflict();
    }

    private Set<NodeState> findNodesFor(CapabilityInternal capability) {
        String capabilityId = capability.getCapabilityId();
        Set<NodeState> nodes = capabilityWithoutVersionToNodes.get(capabilityId);
        if (nodes == null) {
            nodes = Sets.newLinkedHashSet();
            capabilityWithoutVersionToNodes.put(capabilityId, nodes);
        }

        return nodes;
    }

    @Override
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    @Override
    public void resolveNextConflict(Action<ConflictResolutionResult> resolutionAction) {
        CapabilityConflict conflict = conflicts.poll();
        Details details = new Details(conflict);
        for (Resolver resolver : resolvers) {
            resolver.resolve(details);
            if (details.hasResult()) {
                resolutionAction.execute(details);
                CapabilityInternal capability = (CapabilityInternal) conflict.descriptors.iterator().next();
                details.getSelected().getComponent().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(Describables.of("latest version of capability", capability.getCapabilityId())));
                return;
            }
        }
    }

    @Override
    public void registerResolver(Resolver conflictResolver) {
        resolvers.add(conflictResolver);
    }

    public static CapabilitiesConflictHandler.Candidate candidate(NodeState node, Capability capability, Collection<NodeState> implicitCapabilityProviders) {
        return new Candidate(node, capability, implicitCapabilityProviders);
    }

    private static class Candidate implements CapabilitiesConflictHandler.Candidate {
        private final NodeState node;
        private final Capability capability;
        private final Collection<NodeState> implicitCapabilityProviders;

        public Candidate(NodeState node, Capability capability, Collection<NodeState> implicitCapabilityProviders) {
            this.node = node;
            this.capability = capability;
            this.implicitCapabilityProviders = implicitCapabilityProviders;
        }

        @Override
        public NodeState getNode() {
            return node;
        }

        @Override
        public Capability getCapability() {
            return capability;
        }

        @Override
        public Collection<NodeState> getImplicitCapabilityProviders() {
            return implicitCapabilityProviders;
        }
    }


    private static class Details implements ResolutionDetails {
        private final CapabilityConflict conflict;
        private final Set<NodeState> evicted = Sets.newHashSet();
        private NodeState selected;

        private Details(CapabilityConflict conflict) {
            this.conflict = conflict;
        }

        @Override
        public Collection<? extends Capability> getCapabilityVersions() {
            return conflict.descriptors;
        }

        @Override
        public Collection<? extends CandidateDetails> getCandidates(Capability capability) {
            ImmutableList.Builder<CandidateDetails> candidates = new ImmutableList.Builder<CandidateDetails>();
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
            Set<ModuleIdentifier> seen = Sets.newHashSet();
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
        public NodeState getSelected() {
            return selected;
        }
    }

    private static class CapabilityConflict {

        private final Collection<NodeState> nodes;
        private final Set<Capability> descriptors;

        private CapabilityConflict(String group, String name, Collection<NodeState> nodes) {
            this.nodes = nodes;
            final ImmutableSet.Builder<Capability> builder = new ImmutableSet.Builder<Capability>();
            for (final NodeState node : nodes) {
                Capability capability = node.findCapability(group, name);
                if (capability != null) {
                    builder.add(capability);
                }
            }
            this.descriptors = builder.build();
        }

    }

    private static String prettifyCapabilities(CapabilityConflict conflict) {
        TreeSet<String> capabilities = Sets.newTreeSet();
        for (Capability c : conflict.descriptors) {
            capabilities.add(c.getGroup() + ":" + c.getName() + ":" + c.getVersion());
        }
        return Joiner.on(", ").join(capabilities);
    }

    private static String prettifyCandidates(CapabilityConflict conflict) {
        TreeSet<String> candidates = Sets.newTreeSet();
        boolean showVariant = sameComponentAppearsMultipleTimes(conflict);
        for (NodeState node : conflict.nodes) {
            candidates.add(showVariant ? node.getNameWithVariant() : node.getSimpleName());
        }
        return Joiner.on(" and ").join(candidates);
    }

    private static boolean sameComponentAppearsMultipleTimes(CapabilityConflict conflict) {
        Set<ComponentState> components = Sets.newHashSet();
        for (NodeState node : conflict.nodes) {
            if (!components.add(node.getComponent())) {
                return true;
            }
        }
        return false;
    }
}
