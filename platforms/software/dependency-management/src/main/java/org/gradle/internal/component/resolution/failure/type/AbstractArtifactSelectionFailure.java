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
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure;

/**
 * An abstract {@link ArtifactSelectionFailure} that represents the situation when an artifact is requested
 * for a variant and this request fails.
 */
public abstract class AbstractArtifactSelectionFailure extends AbstractResolutionFailure implements ArtifactSelectionFailure {
    private final ComponentIdentifier targetComponent;
    private final String targetVariant;
    private final ImmutableAttributes requestedAttributes;

    public AbstractArtifactSelectionFailure(ResolutionFailureProblemId problemId, ComponentIdentifier targetComponent, String targetVariant, AttributeContainerInternal requestedAttributes) {
        super(problemId);
        this.targetComponent = targetComponent;
        this.targetVariant = targetVariant;
        this.requestedAttributes = requestedAttributes.asImmutable();
    }

    @Override
    public String describeRequestTarget() {
        return targetVariant;
    }

    @Override
    public ComponentIdentifier getTargetComponent() {
        return targetComponent;
    }

    @Override
    public String getTargetVariant() {
        return targetVariant;
    }

    @Override
    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }
}
