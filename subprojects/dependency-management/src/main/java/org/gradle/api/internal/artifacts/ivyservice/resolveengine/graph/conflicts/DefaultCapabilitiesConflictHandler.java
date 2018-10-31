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
    private final List<Resolver> resolvers = Lists.newArrayListWithExpectedSize(2);
    private final Map<String, Set<ComponentState>> capabilityWithoutVersionToComponents = Maps.newHashMap();
    private final Deque<CapabilityConflict> conflicts = new ArrayDeque<CapabilityConflict>();

    @Override
    public PotentialConflict registerCandidate(CapabilitiesConflictHandler.Candidate candidate) {
        CapabilityInternal capability = (CapabilityInternal) candidate.getCapability();
        String group = capability.getGroup();
        String name = capability.getName();
        final Set<ComponentState> components = findComponentsFor(capability);
        components.addAll(candidate.getImplicitCapabilityProviders());
        if (components.add(candidate.getComponent()) && components.size() > 1) {
            // The registered components may contain components which are no longer selected.
            // We don't remove them from the list in the first place because it proved to be
            // slower than filtering as needed.
            final List<ComponentState> candidatesForConflict = Lists.newArrayListWithCapacity(components.size());
            for (ComponentState component : components) {
                if (component.isCandidateForConflictResolution()) {
                    candidatesForConflict.add(component);
                }
            }
            if (candidatesForConflict.size() > 1) {
                PotentialConflict conflict = new PotentialConflict() {
                    @Override
                    public void withParticipatingModules(Action<ModuleIdentifier> action) {
                        for (ComponentState component : candidatesForConflict) {
                            action.execute(component.getId().getModule());
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

    private Set<ComponentState> findComponentsFor(CapabilityInternal capability) {
        String capabilityId = capability.getCapabilityId();
        Set<ComponentState> componentStates = capabilityWithoutVersionToComponents.get(capabilityId);
        if (componentStates == null) {
            componentStates = Sets.newLinkedHashSet();
            capabilityWithoutVersionToComponents.put(capabilityId, componentStates);
        }

        return componentStates;
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
                details.getSelected().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(Describables.of("latest version of capability", capability.getCapabilityId())));
                return;
            }
        }
        throw new RuntimeException("Cannot choose between " + prettifyCandidates(conflict) + " because they provide the same capability: " + prettifyCapabilities(conflict));
    }

    @Override
    public void registerResolver(Resolver conflictResolver) {
        resolvers.add(conflictResolver);
    }

    public static CapabilitiesConflictHandler.Candidate candidate(ComponentState component, Capability capability, Collection<ComponentState> implicitCapabilityProviders) {
        return new Candidate(component, capability, implicitCapabilityProviders);
    }

    private static class Candidate implements CapabilitiesConflictHandler.Candidate {
        private final ComponentState component;
        private final Capability capability;
        private final Collection<ComponentState> implicitCapabilityProviders;

        public Candidate(ComponentState component, Capability capability, Collection<ComponentState> implicitCapabilityProviders) {
            this.component = component;
            this.capability = capability;
            this.implicitCapabilityProviders = implicitCapabilityProviders;
        }

        public ComponentState getComponent() {
            return component;
        }

        public Capability getCapability() {
            return capability;
        }

        @Override
        public Collection<ComponentState> getImplicitCapabilityProviders() {
            return implicitCapabilityProviders;
        }
    }


    private static class Details implements ResolutionDetails {
        private final CapabilityConflict conflict;
        private final Set<ComponentState> evicted = Sets.newHashSet();
        private ComponentState selected;

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
            for (final ComponentState component : conflict.components) {
                if (!evicted.contains(component)) {
                    Capability componentCapability = component.findCapability(group, name);
                    if (componentCapability != null && componentCapability.getVersion().equals(version)) {
                        candidates.add(new CandidateDetails() {
                            @Override
                            public ComponentIdentifier getId() {
                                return component.getComponentId();
                            }

                            @Override
                            public void evict() {
                                evicted.add(component);
                            }

                            @Override
                            public void select() {
                                selected = component;
                            }
                        });
                    }
                }
            }
            return candidates.build();
        }

        @Override
        public void withParticipatingModules(Action<? super ModuleIdentifier> action) {
            Set<ModuleIdentifier> seen = Sets.newHashSet();
            for (ComponentState component : conflict.components) {
                ModuleIdentifier module = component.getId().getModule();
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
            return selected;
        }
    }

    private static class CapabilityConflict {

        private final Collection<ComponentState> components;
        private final Set<Capability> descriptors;

        private CapabilityConflict(String group, String name, Collection<ComponentState> components) {
            this.components = components;
            final ImmutableSet.Builder<Capability> builder = new ImmutableSet.Builder<Capability>();
            for (final ComponentState component : components) {
                Capability capability = component.findCapability(group, name);
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
        for (ComponentState component : conflict.components) {
            candidates.add(component.getId().toString());
        }
        return Joiner.on(" and ").join(candidates);
    }
}
