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

package org.gradle.internal.component;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

import java.util.List;

public class ResolutionFailure {
    private final AttributesSchemaInternal schema;
    private final ResolutionFailureType type;
    private final ComponentGraphResolveMetadata targetComponent;
    private final String requestedName;
    private final ImmutableAttributes requestedAttributes;
    private final ImmutableList<AssessedCandidate> candidates;
    private final boolean isVariantAware;

    public ResolutionFailure(AttributesSchemaInternal schema, ResolutionFailureType type, ComponentGraphResolveMetadata targetComponent, String requestedName, AttributeContainerInternal requestedAttributes, List<AssessedCandidate> candidates, boolean isVariantAware) {
        this.schema = schema;
        this.type = type;
        this.targetComponent = targetComponent;
        this.requestedName = requestedName;
        this.requestedAttributes = requestedAttributes.asImmutable();
        this.candidates = ImmutableList.copyOf(candidates);
        this.isVariantAware = isVariantAware;
    }

    public AttributesSchemaInternal getSchema() {
        return schema;
    }

    public ResolutionFailureType getType() {
        return type;
    }

    public ComponentGraphResolveMetadata getTargetComponent() {
        return targetComponent;
    }

    public String getRequestedName() {
        return requestedName;
    }

    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }

    public ImmutableList<AssessedCandidate> getCandidates() {
        return candidates;
    }

    public boolean isVariantAware() {
        return isVariantAware;
    }

    public boolean noCandidatesHaveAttributes() {
        return candidates.stream().allMatch(AssessedCandidate::hasNoAttributes);
    }

    public boolean allCandidatesAreIncompatible() {
        return candidates.stream().noneMatch(candidate -> candidate.getIncompatibleAttributes().isEmpty());
    }

    public enum ResolutionFailureType {
        AMBIGUOUS_VARIANTS,
        INCOMPATIBLE_VARIANTS,
        NO_MATCHING_CONFIGURATIONS,
        NO_MATCHING_VARIANTS;
    }
}
