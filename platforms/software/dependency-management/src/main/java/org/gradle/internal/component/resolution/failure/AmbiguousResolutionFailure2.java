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

package org.gradle.internal.component.resolution.failure;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.List;

public class AmbiguousResolutionFailure2 extends ResolutionFailure2 {
    @Nullable
    private final ComponentGraphResolveMetadata targetComponent;
    private final ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> candidates;
    private final boolean isVariantAware;

    public AmbiguousResolutionFailure2(AttributesSchemaInternal schema, @Nullable ComponentGraphResolveMetadata targetComponent, String requestedName, AttributeContainerInternal requestedAttributes, List<ResolutionCandidateAssessor.AssessedCandidate> candidates, boolean isVariantAware) {
        super(schema, requestedName, requestedAttributes);
        this.targetComponent = targetComponent;
        this.candidates = ImmutableList.copyOf(candidates);
        this.isVariantAware = isVariantAware;
    }

    @Nullable
    public ComponentGraphResolveMetadata getTargetComponent() {
        return targetComponent;
    }

    public ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> getCandidates() {
        return candidates;
    }

    public boolean isVariantAware() {
        return isVariantAware;
    }
}
