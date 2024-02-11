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
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;

import java.util.List;

/**
 * A {@link ResolutionFailure} that represents the generic situation when no variants can
 * be found in the list of candidates that are compatible with a request.
 */
public class IncompatibleResolutionFailure extends AbstractIncompatibleAttributesSelectionFailure {
    private final ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> candidates;

    public IncompatibleResolutionFailure(AttributesSchemaInternal schema, String requestedName, AttributeContainerInternal requestedAttributes, List<ResolutionCandidateAssessor.AssessedCandidate> candidates) {
        super(schema, requestedName, requestedAttributes);
        this.candidates = ImmutableList.copyOf(candidates);
    }

    public ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> getCandidates() {
        return candidates;
    }
}
