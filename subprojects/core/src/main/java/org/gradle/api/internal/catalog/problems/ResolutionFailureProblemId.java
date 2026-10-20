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
import org.gradle.api.problems.DependenciesProblemGroup;
import org.gradle.api.problems.ProblemGroups;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.SecondLevelProblemGroup;
import org.jspecify.annotations.NullMarked;

/**
 * Problem IDs for Variant Selection resolution failure problems.
 *
 * These should correspond with the <em>non-abstract failures classes</em> of the {@code ResolutionFailure} type hierarchy.
 * in the {@code org.gradle.internal.component.resolution.failure.type} package.
 * <p>
 * The problems are reported in {@code Dependencies > Graph Resolution} or, for failures selecting artifacts after graph
 * resolution, {@code Dependencies > Artifact Resolution}, named by their display name.
 */
@NullMarked
public enum ResolutionFailureProblemId implements Describable {
    // Component Selection failures
    NO_VERSION_SATISFIES("No version satisfies the constraints", Phase.GRAPH_RESOLUTION),
    CAPABILITY_CONFLICT("Module rejected due to a capability conflict", Phase.GRAPH_RESOLUTION),

    // Variant Selection failures
    CONFIGURATION_NOT_COMPATIBLE("Configuration selected by name is not compatible", Phase.GRAPH_RESOLUTION),
    CONFIGURATION_NOT_CONSUMABLE("Configuration selected by name is not consumable", Phase.GRAPH_RESOLUTION),
    CONFIGURATION_DOES_NOT_EXIST("Configuration selected by name does not exist", Phase.GRAPH_RESOLUTION),
    AMBIGUOUS_VARIANTS("Multiple variants exist that would match the request", Phase.GRAPH_RESOLUTION),
    NO_COMPATIBLE_VARIANTS("No variants exist that would match the request", Phase.GRAPH_RESOLUTION),
    NO_VARIANTS_WITH_MATCHING_CAPABILITIES("No variants exist with capabilities that would match the request", Phase.GRAPH_RESOLUTION),

    // Graph Validation failures
    INCOMPATIBLE_MULTIPLE_NODES("Incompatible nodes of a single component were selected", Phase.GRAPH_RESOLUTION),

    // Artifact Selection failures
    AMBIGUOUS_ARTIFACT_TRANSFORM("Multiple artifact transforms exist that would satisfy the request", Phase.ARTIFACT_RESOLUTION),
    NO_COMPATIBLE_ARTIFACT("No artifacts exist that would match the request", Phase.ARTIFACT_RESOLUTION),
    AMBIGUOUS_ARTIFACTS("Multiple artifacts exist that would match the request", Phase.ARTIFACT_RESOLUTION),
    UNKNOWN_ARTIFACT_SELECTION_FAILURE("Unknown artifact selection failure", Phase.ARTIFACT_RESOLUTION),

    /**
     * Indicates that the resolution failed for an unknown reason not enumerated above.
     */
    UNKNOWN_RESOLUTION_FAILURE("Unknown resolution failure", Phase.GRAPH_RESOLUTION);

    private final String displayName;
    private final Phase phase;

    ResolutionFailureProblemId(String displayName, Phase phase) {
        this.displayName = displayName;
        this.phase = phase;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public ProblemId problemId(ProblemGroups groups) {
        return phase.group(groups.getDependencies()).problemId(displayName);
    }

    private enum Phase {
        GRAPH_RESOLUTION {
            @Override
            SecondLevelProblemGroup group(DependenciesProblemGroup dependencies) {
                return dependencies.getGraphResolution();
            }
        },
        ARTIFACT_RESOLUTION {
            @Override
            SecondLevelProblemGroup group(DependenciesProblemGroup dependencies) {
                return dependencies.getArtifactResolution();
            }
        };

        abstract SecondLevelProblemGroup group(DependenciesProblemGroup dependencies);
    }
}
