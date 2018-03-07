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
package org.gradle.api.internal.artifacts.dsl;

import com.google.common.base.Joiner;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilitiesHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilityInternal;

import javax.annotation.Nullable;
import java.util.Collection;

public class CapabilitiesAwareModuleReplacements implements ModuleReplacementsData {
    private final ModuleReplacementsData delegate;
    private final CapabilitiesHandlerInternal capabilitiesHandler;

    public CapabilitiesAwareModuleReplacements(ModuleReplacementsData delegate, CapabilitiesHandlerInternal capabilitiesHandler) {
        this.delegate = delegate;
        this.capabilitiesHandler = capabilitiesHandler;
    }

    @Nullable
    @Override
    public Replacement getReplacementFor(ModuleIdentifier sourceModule) {
        Replacement replacement = delegate.getReplacementFor(sourceModule);
        if (replacement == null && capabilitiesHandler.hasCapabilities()) {
            Collection<? extends CapabilityInternal> capabilities = capabilitiesHandler.getCapabilities(sourceModule);
            for (CapabilityInternal capability : capabilities) {
                ModuleIdentifier prefer = capability.getPrefer();
                if (prefer != null && !sourceModule.equals(prefer)) {
                    replacement = createReplacementForCapability(capability, prefer);
                    break;
                }
            }
        }
        return replacement;
    }

    private static Replacement createReplacementForCapability(CapabilityInternal capability, ModuleIdentifier prefer) {
        String because = capability.getReason();
        if (because == null) {
            because = "capability " + capability.getCapabilityId() + " is provided by " + Joiner.on(" and ").join(capability.getProvidedBy());
        }
        return new Replacement(prefer, because);
    }

    @Override
    public boolean participatesInReplacements(ModuleIdentifier moduleId) {
        boolean participates = delegate.participatesInReplacements(moduleId);
        if (!participates && capabilitiesHandler.hasCapabilities()) {
            Collection<? extends CapabilityInternal> capabilities = capabilitiesHandler.getCapabilities(moduleId);
            for (CapabilityInternal capability : capabilities) {
                if (capability.getPrefer() != null) {
                    // there's only a potential conflict if a preference is set.
                    // if we return true when there's any capability, conflict resolution could choose automatically
                    // between 2 modules that have _not_ set any preference, which would be wrong
                    return true;
                }
            }
            return false;
        }
        return participates;
    }
}
