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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.resolution.failure.CapabilitiesDescriber;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.NoMatchingCapabilitiesFailure;

import java.util.List;
import java.util.Optional;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link NoMatchingCapabilitiesFailure}.
 */
public abstract class NoMatchingCapabilitiesFailureDescriber extends AbstractResolutionFailureDescriber<NoMatchingCapabilitiesFailure> {
    @Override
    public VariantSelectionException describeFailure(NoMatchingCapabilitiesFailure failure, Optional<AttributesSchemaInternal> schema) {
        String message = buildNoMatchingCapabilitiesFailureMsg(failure);
        List<String> resolutions = buildResolutions(suggestReviewAlgorithm());
        return new VariantSelectionException(message, failure, resolutions);
    }

    private String buildNoMatchingCapabilitiesFailureMsg(NoMatchingCapabilitiesFailure failure) {
        StringBuilder sb = new StringBuilder("Unable to find a variant of ");
        sb.append(failure.getRequestedName()).append(" providing the requested ");
        sb.append(CapabilitiesDescriber.describeCapabilitiesWithTitle(failure.getTargetComponentId(), failure.getRequestedCapabilities()));
        sb.append(":\n");
        for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
            sb.append("   - Variant ").append(candidate.getDisplayName()).append(" provides ");
            sb.append(CapabilitiesDescriber.describeCapabilities(failure.getTargetComponentId(), candidate.getCandidateCapabilities().asSet())).append("\n");
        }
        return sb.toString();
    }
}
