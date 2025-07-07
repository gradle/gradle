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

package org.gradle.api.internal.catalog.problems;

import org.gradle.api.Describable;
import org.jspecify.annotations.NullMarked;

/**
 * Problem IDs for Variant Selection resolution failure problems.
 *
 * These should correspond with the <em>non-abstract failures classes</em> of the {@code ResolutionFailure} type hierarchy.
 * in the {@code org.gradle.internal.component.resolution.failure.type} package.
 */
@NullMarked
public enum ResolutionFailureProblemId implements Describable {
    // Variant Selection failures
    CONFIGURATION_NOT_COMPATIBLE("Configuration selected by name is not compatible"),
    CONFIGURATION_NOT_CONSUMABLE("Configuration selected by name is not consumable"),
    CONFIGURATION_DOES_NOT_EXIST("Configuration selected by name does not exist"),
    AMBIGUOUS_VARIANTS("Multiple variants exist that would match the request"),
    NO_COMPATIBLE_VARIANTS("No variants exist that would match the request"),
    NO_VARIANTS_WITH_MATCHING_CAPABILITIES("No variants exist with capabilities that would match the request"),

    // Graph Validation failures
    INCOMPATIBLE_MULTIPLE_NODES("Incompatible nodes of a single component were selected"),

    // Artifact Selection failures
    AMBIGUOUS_ARTIFACT_TRANSFORM("Multiple artifacts transforms exist that would satisfy the request"),
    NO_COMPATIBLE_ARTIFACT("No artifacts exist that would match the request"),
    AMBIGUOUS_ARTIFACTS("Multiple artifacts exist that would match the request"),
    UNKNOWN_ARTIFACT_SELECTION_FAILURE("Unknown artifact selection failure"),

    /**
     * Indicates that the resolution failed for an unknown reason not enumerated above.
     */
    UNKNOWN_RESOLUTION_FAILURE("Unknown resolution failure");

    private final String displayName;

    ResolutionFailureProblemId(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
