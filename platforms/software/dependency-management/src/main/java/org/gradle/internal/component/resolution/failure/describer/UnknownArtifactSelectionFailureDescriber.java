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

import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException;
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure;

import java.util.List;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link UnknownArtifactSelectionFailure}.
 *
 * This type also will wrap an existing exception of unknown type into an {@link ArtifactSelectionException}.  If a
 * {@link ArtifactSelectionException} is already the cause of the failure, it will be returned directly, with resolution
 * information added as necessary.
 */
public abstract class UnknownArtifactSelectionFailureDescriber extends AbstractResolutionFailureDescriber<UnknownArtifactSelectionFailure> {
    @Override
    public ArtifactSelectionException describeFailure(UnknownArtifactSelectionFailure failure) {
        final ArtifactSelectionException result;
        if (failure.getCause() instanceof ArtifactSelectionException) {
            result = (ArtifactSelectionException) failure.getCause();
        } else {
            String message = buildFailureMsg(failure);
            List<String> resolutions = buildResolutions(suggestReviewAlgorithm());
            result = new ArtifactSelectionException(message, failure, resolutions, failure.getCause());
        }
        return result;
    }

    private String buildFailureMsg(UnknownArtifactSelectionFailure failure) {
        return String.format("Could not select a variant of %s that matches the consumer attributes.", failure.describeRequestTarget());
    }
}
