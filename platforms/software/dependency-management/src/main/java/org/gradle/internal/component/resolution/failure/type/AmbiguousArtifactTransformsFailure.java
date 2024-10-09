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
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure;

import java.util.List;

/**
 * An {@link ArtifactSelectionFailure} that represents the situation when multiple artifact transforms are
 * available that would satisfy an artifact selection request.
 */
public final class AmbiguousArtifactTransformsFailure extends AbstractArtifactSelectionFailure {
    /*
     * TODO: We need to keep track of the transformed variant and transformation chains that are available
     * to satisfy the artifact selection request somehow (so that failure describers can investigate it), but
     * we should use a much simpler stateless data-only type (without Project and Gradle references) for this.
     *
     * This type causes issues with BuildOperationTrace serialization and really isn't the right place to use
     * this type...but this is what was originally used to describe these kind of failures, so it remains for now
     * until we have a chance to refactor this.
     */
    private final ImmutableList<TransformedVariant> transformedVariants;

    public AmbiguousArtifactTransformsFailure(ComponentIdentifier targetComponent, String targetVariant, AttributeContainerInternal requestedAttributes, List<TransformedVariant> transformedVariants) {
        super(ResolutionFailureProblemId.AMBIGUOUS_ARTIFACT_TRANSFORM, targetComponent, targetVariant, requestedAttributes);
        this.transformedVariants = ImmutableList.copyOf(transformedVariants);
    }

    public ImmutableList<TransformedVariant> getTransformedVariants() {
        return transformedVariants;
    }
}
