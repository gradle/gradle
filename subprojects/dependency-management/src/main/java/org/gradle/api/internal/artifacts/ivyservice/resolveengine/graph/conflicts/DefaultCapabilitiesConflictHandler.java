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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.capabilities.CapabilityDescriptor;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;

public class DefaultCapabilitiesConflictHandler implements CapabilitiesConflictHandler {
    private final Multimap<ModuleIdentifier, ComponentWithCapability> capabilityToComponents = HashMultimap.create();
    private final Deque<CapabilityConflict> conflicts = new ArrayDeque<CapabilityConflict>();

    @Override
    public PotentialConflict registerModule(Candidate newModule) {
        CapabilityDescriptor capabilityDescriptor = newModule.getCapabilityDescriptor();
        ModuleIdentifier capabilityWithoutVersion = DefaultModuleIdentifier.newId(capabilityDescriptor.getGroup(), capabilityDescriptor.getName());
        capabilityToComponents.put(capabilityWithoutVersion, new ComponentWithCapability(capabilityDescriptor, newModule.getComponent()));
        Collection<ComponentWithCapability> states = capabilityToComponents.get(capabilityWithoutVersion);
        if (states.size() > 1) {
            final List<ComponentWithCapability> currentlySelected = Lists.newArrayListWithCapacity(states.size());
            for (ComponentWithCapability state : states) {
                if (state.component.isSelected()) {
                    currentlySelected.add(state);
                }
            }
            if (currentlySelected.size() > 1) {
                PotentialConflict conflict = new PotentialConflict() {
                    @Override
                    public void withParticipatingModules(Action<ModuleIdentifier> action) {
                        for (ComponentWithCapability state : currentlySelected) {
                            action.execute(state.component.getId().getModule());
                        }
                    }

                    @Override
                    public boolean conflictExists() {
                        return true;
                    }
                };
                conflicts.add(new CapabilityConflict(currentlySelected));
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
    public void resolveNextConflict(Action<Void> resolutionAction) {
        CapabilityConflict conflict = conflicts.poll();
        throw new RuntimeException("Cannot choose between " + prettifyCandidates(conflict) + " because they provide the same capability: " + prettifyCapabilities(conflict));
    }

    @Override
    public void registerResolver(Void conflictResolver) {

    }

    private static class ComponentWithCapability {
        private final CapabilityDescriptor capability;
        private final ComponentState component;

        private ComponentWithCapability(CapabilityDescriptor capability, ComponentState component) {
            this.capability = capability;
            this.component = component;
        }
    }

    private static class CapabilityConflict {

        private final List<ComponentWithCapability> components;
        private CapabilityConflict(List<ComponentWithCapability> components) {
            this.components = components;
        }

    }

    private static String prettifyCapabilities(CapabilityConflict conflict) {
        TreeSet<String> capabilities = Sets.newTreeSet();
        for (ComponentWithCapability component : conflict.components) {
            CapabilityDescriptor c = component.capability;
            capabilities.add(c.getGroup() + ":" + c.getName() + ":" + c.getVersion());
        }
        return Joiner.on(", ").join(capabilities);
    }

    private static String prettifyCandidates(CapabilityConflict conflict) {
        TreeSet<String> candidates = Sets.newTreeSet();
        for (ComponentWithCapability component : conflict.components) {
            candidates.add(component.component.getId().toString());
        }
        return Joiner.on(" and ").join(candidates);
    }
}
