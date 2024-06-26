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
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.resolution.failure.interfaces.VariantSelectionByAttributesFailure;

/**
 * An abstract {@link VariantSelectionByAttributesFailure} that represents the situation when a variant
 * was requested via variant-aware matching and that matching failed.
 */
public abstract class AbstractVariantSelectionByAttributesFailure extends AbstractResolutionFailure implements VariantSelectionByAttributesFailure {
    private final ComponentIdentifier targetComponent;
    private final ImmutableAttributes requestedAttributes;
    private final ImmutableCapabilities requestedCapabilities;

    public AbstractVariantSelectionByAttributesFailure(ResolutionFailureProblemId problemId, ComponentIdentifier targetComponent, AttributeContainerInternal requestedAttributes, ImmutableCapabilities requestedCapabilities) {
        super(problemId);
        this.targetComponent = targetComponent;
        this.requestedAttributes = requestedAttributes.asImmutable();
        this.requestedCapabilities = requestedCapabilities;
    }

    @Override
    public String describeRequestTarget() {
        return targetComponent.getDisplayName();
    }

    @Override
    public ComponentIdentifier getTargetComponent() {
        return targetComponent;
    }

    @Override
    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }

    @Override
    public ImmutableCapabilities getRequestedCapabilities() {
        return requestedCapabilities;
    }
}
