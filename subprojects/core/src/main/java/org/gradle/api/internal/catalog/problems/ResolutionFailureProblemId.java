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

import org.gradle.api.NonNullApi;

/**
 * Problem IDs for Variant Selection resolution failure problems.
 *
 * These should correspond with the <em>non-abstract failures classes</em> of the {@code ResolutionFailure} type hierarchy.
 * in the {@code org.gradle.internal.component.resolution.failure.type} package.
 */
@NonNullApi
public enum ResolutionFailureProblemId {
    // Stage 2 failures
    CONFIGURATION_NOT_COMPATIBLE,
    CONFIGURATION_NOT_CONSUMABLE,
    CONFIGURATION_DOES_NOT_EXIST,
    AMBIGUOUS_VARIANTS,
    NO_COMPATIBLE_VARIANTS,
    NO_VARIANTS_WITH_MATCHING_CAPABILITIES,

    // Stage 3 failures
    AMBIGUOUS_ARTIFACT_TRANSFORM,
    NO_COMPATIBLE_ARTIFACT,
    AMBIGUOUS_ARTIFACTS,
    UNKNOWN_ARTIFACT_SELECTION_FAILURE,

    // Stage 4 failures
    INCOMPATIBLE_MULTIPLE_NODES,

    /**
     * Indicates that the resolution failed for an unknown reason not enumerated above.
     */
    UNKNOWN_RESOLUTION_FAILURE
}
