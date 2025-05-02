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
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure;

/**
 * An {@link ArtifactSelectionFailure} that represents an unknown, catch-all failure that occurred during artifact selection.
 */
public final class UnknownArtifactSelectionFailure extends AbstractArtifactSelectionFailure {
    private final Exception cause;

    public UnknownArtifactSelectionFailure(ComponentIdentifier targetComponent, String targetVariant, AttributeContainerInternal requestedAttributes, Exception cause) {
        super(ResolutionFailureProblemId.UNKNOWN_ARTIFACT_SELECTION_FAILURE, targetComponent, targetVariant, requestedAttributes);
        this.cause = cause;
    }

    public Exception getCause() {
        return cause;
    }
}
