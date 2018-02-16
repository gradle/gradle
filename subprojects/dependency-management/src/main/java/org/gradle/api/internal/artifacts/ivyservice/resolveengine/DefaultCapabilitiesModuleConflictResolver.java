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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.base.Joiner;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilitiesHandlerInternal;

import java.util.Collection;

/**
 * This conflict resolver is trying to resolve conflicts based on the capabilities of the conflicting modules.
 * If more than one module is in conflict and that they provide the same capability, it tries to find if
 * there's a preferred module for the capability, in which case it's going to be selected.
 */
class DefaultCapabilitiesModuleConflictResolver implements ModuleConflictResolver {
    private final CapabilitiesHandlerInternal capabilitiesHandler;

    DefaultCapabilitiesModuleConflictResolver(CapabilitiesHandlerInternal capabilitiesHandler) {
        this.capabilitiesHandler = capabilitiesHandler;
    }

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        if (capabilitiesHandler.hasCapabilities()) {
            // todo CC: can be optimized further, if needed, by only creating the multimap if there's actually at least one capability provided by more than one module
            Collection<? extends T> candidates = details.getCandidates();
            Multimap<String, ModuleIdentifier> capabilitiesToCandidates = LinkedHashMultimap.create();
            for (T candidate : candidates) {
                ModuleIdentifier module = candidate.getId().getModule();
                capabilitiesHandler.recordCapabilities(module, capabilitiesToCandidates);
            }
            if (capabilitiesToCandidates.isEmpty()) {
                return;
            }
            tryPreferredCapabilityProvider(details, candidates, capabilitiesToCandidates);
        }
    }

    private <T extends ComponentResolutionState> void tryPreferredCapabilityProvider(ConflictResolverDetails<T> details, Collection<? extends T> candidates, Multimap<String, ModuleIdentifier> capabilitiesToCandidates) {
        for (String capability : capabilitiesToCandidates.keySet()) {
            Collection<ModuleIdentifier> allModulesProvidingCapability = capabilitiesToCandidates.get(capability);
            int inConflict = 0;
            for (ModuleIdentifier participant : details.getParticipants()) {
                if (allModulesProvidingCapability.contains(participant)) {
                    inConflict++;
                }
            }
            if (inConflict > 1) {
                // there's a conflict between different modules with the same capability
                ModuleIdentifier preferred = capabilitiesHandler.getPreferred(capability);
                if (preferred == null) {
                    throw new RuntimeException("Cannot choose between " + Joiner.on(" or ").join(details.getParticipants()) + " because they provide the same capability: " + capability);
                }
                // todo: handle case when there are more than one capabilities
                for (T candidate : candidates) {
                    if (candidate.getId().getModule().equals(preferred)) {
                        details.select(candidate);
                        return;
                    }
                }
            }
        }
    }

}
