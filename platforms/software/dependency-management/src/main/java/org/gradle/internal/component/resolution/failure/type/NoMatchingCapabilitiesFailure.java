/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.type;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;

import java.util.Collection;
import java.util.List;

/**
 * A {@link ResolutionFailure} that represents the situation when no variants can
 * be found in the list of candidates that have the requested capabilities.
 */
public final class NoMatchingCapabilitiesFailure extends AbstractVariantSelectionFailure {
    private final Collection<? extends Capability> requestedCapabilities;
    private final List<AssessedCandidate> candidates;
    private final ModuleVersionIdentifier targetComponentId;

    public NoMatchingCapabilitiesFailure(String requestedName, Collection<? extends Capability> requestedCapabilities, List<AssessedCandidate> candidates, ModuleVersionIdentifier targetComponentId) {
        super(requestedName);
        this.requestedCapabilities = requestedCapabilities;
        this.candidates = candidates;
        this.targetComponentId = targetComponentId;
    }

    public Collection<? extends Capability> getRequestedCapabilities() {
        return requestedCapabilities;
    }

    public List<AssessedCandidate> getCandidates() {
        return candidates;
    }

    public ModuleVersionIdentifier getTargetComponentId() {
        return targetComponentId;
    }
}
