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

import org.gradle.api.capabilities.Capability;

import java.util.Collection;

/**
 * A resolver which will select a candidate when it's the last one available.
 */
public class LastCandidateCapabilityResolver implements CapabilitiesConflictHandler.Resolver {
    @Override
    public void resolve(CapabilitiesConflictHandler.ResolutionDetails details) {
        Collection<? extends Capability> capabilityVersions = details.getCapabilityVersions();
        CapabilitiesConflictHandler.CandidateDetails single = null;
        for (Capability capabilityVersion : capabilityVersions) {
            Collection<? extends CapabilitiesConflictHandler.CandidateDetails> candidates = details.getCandidates(capabilityVersion);
            int size = candidates.size();
            if (size >= 1) {
                if (size == 1 && single == null) {
                    single = candidates.iterator().next();
                } else {
                    // not a single candidate
                    return;
                }
            }
        }
        if (single != null) {
            single.select();
        }
    }
}
