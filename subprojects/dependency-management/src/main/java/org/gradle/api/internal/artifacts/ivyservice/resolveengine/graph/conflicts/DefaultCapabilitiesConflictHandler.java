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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.CapabilityDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.external.model.CapabilityInternal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DefaultCapabilitiesConflictHandler implements CapabilitiesConflictHandler {
    private final List<Resolver> resolvers = Lists.newArrayListWithExpectedSize(2);
    private final Multimap<String, ComponentState> capabilityWithoutVersionToComponents = HashMultimap.create();
    private final Deque<CapabilityConflict> conflicts = new ArrayDeque<CapabilityConflict>();

    @Override
    public PotentialConflict registerModule(CapabilitiesConflictHandler.Candidate newModule) {
        CapabilityInternal capabilityDescriptor = (CapabilityInternal) newModule.getCapabilityDescriptor();
        String group = capabilityDescriptor.getGroup();
        String name = capabilityDescriptor.getName();
        Collection<ComponentState> components = capabilityWithoutVersionToComponents.get(capabilityDescriptor.getCapabilityId());
        if (components.add(newModule.getComponent()) && components.size() > 1) {
            final List<ComponentState> currentlySelected = Lists.newArrayListWithCapacity(components.size());
            for (ComponentState component : components) {
                if (component.isSelected()) {
                    currentlySelected.add(component);
                }
            }
            if (currentlySelected.size() > 1) {
                PotentialConflict conflict = new PotentialConflict() {
                    @Override
                    public void withParticipatingModules(Action<ModuleIdentifier> action) {
                        for (ComponentState component : currentlySelected) {
                            action.execute(component.getId().getModule());
                        }
                    }

                    @Override
                    public boolean conflictExists() {
                        return true;
                    }
                };
                conflicts.add(new CapabilityConflict(group, name, currentlySelected));
                return conflict;
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
        CapabilityConflict conflict = conflicts.poll();
        Details details = new Details(conflict);
        for (Resolver resolver : resolvers) {
            resolver.resolve(details);
            if (details.hasResult()) {
                resolutionAction.execute(details);
                details.getSelected().addCause(VersionSelectionReasons.CONFLICT_RESOLUTION);
                return;
            }
        }
        throw new RuntimeException("Cannot choose between " + prettifyCandidates(conflict) + " because they provide the same capability: " + prettifyCapabilities(conflict));
    }

    @Override
    public void registerResolver(Resolver conflictResolver) {
        resolvers.add(conflictResolver);
    }

    public static CapabilitiesConflictHandler.Candidate candidate(ComponentState component, CapabilityDescriptor capabilityDescriptor) {
        return new Candidate(component, capabilityDescriptor);
    }

    private static class Candidate implements CapabilitiesConflictHandler.Candidate {
        private final ComponentState component;
        private final CapabilityDescriptor capabilityDescriptor;

        public Candidate(ComponentState component, CapabilityDescriptor capabilityDescriptor) {
            this.component = component;
            this.capabilityDescriptor = capabilityDescriptor;
        }

        public ComponentState getComponent() {
            return component;
        }

        public CapabilityDescriptor getCapabilityDescriptor() {
            return capabilityDescriptor;
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
        public Collection<? extends CapabilityDescriptor> getCapabilityVersions() {
            return conflict.descriptors;
        }

        @Override
        public Collection<? extends CandidateDetails> getCandidates(CapabilityDescriptor capability) {
            ImmutableList.Builder<CandidateDetails> candidates = new ImmutableList.Builder<CandidateDetails>();
            String group = capability.getGroup();
            String name = capability.getName();
            String version = capability.getVersion();
            for (final ComponentState component : conflict.components) {
                if (!evicted.contains(component)) {
                    CapabilityDescriptor componentCapability = component.findCapability(group, name);
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

        private final List<ComponentState> components;
        private final Set<CapabilityDescriptor> descriptors;

        private CapabilityConflict(String group, String name, List<ComponentState> components) {
            this.components = components;
            final ImmutableSet.Builder<CapabilityDescriptor> builder = new ImmutableSet.Builder<CapabilityDescriptor>();
            for (final ComponentState component : components) {
                CapabilityDescriptor capability = component.findCapability(group, name);
                if (capability != null) {
                    builder.add(capability);
                }
            }
            this.descriptors = builder.build();
        }

    }

    private static String prettifyCapabilities(CapabilityConflict conflict) {
        TreeSet<String> capabilities = Sets.newTreeSet();
        for (CapabilityDescriptor c : conflict.descriptors) {
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
