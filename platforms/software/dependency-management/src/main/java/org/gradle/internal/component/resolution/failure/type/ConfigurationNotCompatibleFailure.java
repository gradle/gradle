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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.interfaces.VariantSelectionByNameFailure;

import java.util.List;

/**
 * A {@link VariantSelectionByNameFailure} that represents the situation when a configuration is
 * requested by name but its attributes are not compatible with the request.
 */
public final class ConfigurationNotCompatibleFailure extends AbstractVariantSelectionByNameFailure {
    private final ImmutableAttributes requestedAttributes;
    private final ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> candidates;

    public ConfigurationNotCompatibleFailure(ComponentIdentifier targetComponent, String requestedConfigurationName, AttributeContainerInternal requestedAttributes, List<ResolutionCandidateAssessor.AssessedCandidate> candidates) {
        super(ResolutionFailureProblemId.CONFIGURATION_NOT_COMPATIBLE, targetComponent, requestedConfigurationName);
        this.requestedAttributes = requestedAttributes.asImmutable();
        this.candidates = ImmutableList.copyOf(candidates);
    }

    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }

    public ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> getCandidates() {
        return candidates;
    }
}
