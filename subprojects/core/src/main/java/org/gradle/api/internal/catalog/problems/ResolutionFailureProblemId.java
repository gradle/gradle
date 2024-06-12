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

// TODO: Can this type just be put in the package below?  Or should it live here?

import org.gradle.api.NonNullApi;

/**
 * Problem IDs for Variant Selection resolution failure problems.
 *
 * These should correspond with the <em>non-abstract failures classes</em> of the {@code ResolutionFailure} type hierarchy.
 * in the {@code org.gradle.internal.component.resolution.failure.type} package.
 */
@NonNullApi
public enum ResolutionFailureProblemId {
    NO_MATCHING_CAPABILITIES,
    INCOMPATIBLE_RESOLUTION,
    INCOMPATIBLE_REQUESTED_CONFIGURATION,
    INCOMPATIBLE_GRAPH_VARIANT,
    AMBIGUOUS_ARTIFACT_TRANSFORM,
    AMBIGUOUS_RESOLUTION,
    VARIANT_AWARE_AMBIGUOUS_RESOLUTION,
    INCOMPATIBLE_MULTIPLE_NODE_SELECTION,
    UNKNOWN_ARTIFACT_SELECTION,
    CONFIGURATION_NOT_CONSUMABLE,
    REQUESTED_CONFIGURATION_NOT_FOUND,
    EXTERNAL_REQUESTED_CONFIGURATION_NOT_FOUND
}
