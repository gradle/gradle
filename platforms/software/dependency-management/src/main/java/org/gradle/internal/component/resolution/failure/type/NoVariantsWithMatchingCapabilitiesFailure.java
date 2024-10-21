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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.interfaces.VariantSelectionByAttributesFailure;

import java.util.List;

/**
 * A {@link VariantSelectionByAttributesFailure} that represents the situation when no variants can
 * be found in the list of candidates that have the requested capabilities.
 */
public final class NoVariantsWithMatchingCapabilitiesFailure extends AbstractVariantSelectionByAttributesFailure {
    private final ImmutableList<AssessedCandidate> candidates;

    public NoVariantsWithMatchingCapabilitiesFailure(ComponentIdentifier targetComponent, AttributeContainerInternal requestedAttributes, ImmutableSet<CapabilitySelector> capabilitySelectors, List<ResolutionCandidateAssessor.AssessedCandidate> candidates) {
        super(ResolutionFailureProblemId.NO_VARIANTS_WITH_MATCHING_CAPABILITIES, targetComponent, requestedAttributes, capabilitySelectors);
        this.candidates = ImmutableList.copyOf(candidates);
    }

    public List<AssessedCandidate> getCandidates() {
        return candidates;
    }
}
