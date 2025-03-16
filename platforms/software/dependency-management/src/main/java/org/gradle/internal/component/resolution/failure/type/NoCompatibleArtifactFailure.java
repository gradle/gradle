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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;

import java.util.List;

/**
 * An {@link ArtifactSelectionFailure} that represents the situation when an artifact variant cannot
 * be selected because no artifact variants were found that are compatible with the requested attributes.
 */
public final class NoCompatibleArtifactFailure extends AbstractArtifactSelectionFailure {
    private final List<ResolutionCandidateAssessor.AssessedCandidate> candidates;

    public NoCompatibleArtifactFailure(ComponentIdentifier targetComponent, String targetVariant, AttributeContainerInternal requestedAttributes, List<ResolutionCandidateAssessor.AssessedCandidate> candidates) {
        super(ResolutionFailureProblemId.NO_COMPATIBLE_ARTIFACT, targetComponent, targetVariant, requestedAttributes);
        this.candidates = candidates;
    }

    public List<ResolutionCandidateAssessor.AssessedCandidate> getCandidates() {
        return candidates;
    }
}
