/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.internal.component.resolution.failure.SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason;
import org.gradle.internal.component.resolution.failure.exception.ConflictingConstraintsException;
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link ModuleRejectedFailure} where
 * there were multiple constraints involved in a selection failure that each require different versions.
 * <p>
 * Note that this describer will also be used during the {@code dependencyInsight} report when there is a selection failure.
 */
public abstract class ModuleRejectedIncompatibleConstraintsFailureDescriber extends AbstractResolutionFailureDescriber<ModuleRejectedFailure> {
    private static final String DEPENDENCY_INSIGHT_TASK_NAME = "dependencyInsight";
    private static final String DEBUGGING_WITH_DEPENDENCY_INSIGHT_PREFIX = "Debugging using the " + DEPENDENCY_INSIGHT_TASK_NAME + " report is described in more detail at: ";
    private static final String DEBUGGING_WITH_DEPENDENCY_INSIGHT_ID = "viewing_debugging_dependencies";
    private static final String DEBUGGING_WITH_DEPENDENCY_INSIGHT_SECTION = "sec:identifying-reason-dependency-selection";

    @Override
    public boolean canDescribeFailure(ModuleRejectedFailure failure) {
        return findConflictingConstraints(failure).size() > 1;
    }

    private List<AssessedSelectionReason> findConflictingConstraints(ModuleRejectedFailure failure) {
        Map<String, List<AssessedSelectionReason>> versionsByReasons = failure.getAssessedSelection().getReasons().stream()
            .filter(reason -> reason.getCause() == ComponentSelectionCause.CONSTRAINT)
            .collect(Collectors.groupingBy(AssessedSelectionReason::getRequiredVersion));
        return versionsByReasons.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    @Override
    public ConflictingConstraintsException describeFailure(ModuleRejectedFailure failure) {
        return new ConflictingConstraintsException(summarizeFailure(failure), failure, buildResolutions(failure));
    }

    private List<String> buildResolutions(ModuleRejectedFailure failure) {
        List<String> resolutions = new ArrayList<>(failure.getResolutions().size() + 1);
        resolutions.addAll(failure.getResolutions());
        resolutions.add(DEBUGGING_WITH_DEPENDENCY_INSIGHT_PREFIX + getDocumentationRegistry().getDocumentationFor(DEBUGGING_WITH_DEPENDENCY_INSIGHT_ID, DEBUGGING_WITH_DEPENDENCY_INSIGHT_SECTION) + ".");
        return resolutions;
    }

    private String summarizeFailure(ModuleRejectedFailure failure) {
        Map<String, Integer> reasons = findConflictingConstraints(failure).stream()
            .map(this::summarizeReason)
            .collect(Collectors.groupingBy(summary -> summary, Collectors.summingInt(reason -> 1)));

        StringBuilder sb = new StringBuilder("Component is the target of multiple version constraints with conflicting requirements:\n");
        reasons.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                sb.append(entry.getKey());
                int numOtherPaths = entry.getValue() -1;
                if (numOtherPaths > 0) {
                    sb.append(" (").append(numOtherPaths).append(" other path");
                    if (numOtherPaths > 1) {
                        sb.append("s");
                    }
                    sb.append(" to this version)");
                }
                sb.append("\n");
            });

        return sb.toString();
    }

    private String summarizeReason(AssessedSelectionReason reason) {
        StringBuilder sb = new StringBuilder(reason.getRequiredVersion());

        if (reason.getSegmentedSelectionPath().size() > 1) {
            // If the path has more than one segment, it indicates a transitive selection path which could be arbitrarily deep, so show the first segment past the root
            sb.append(" - transitively via ")
                .append(reason.getSegmentedSelectionPath().get(1));
        } else if (reason.isFromLock()) {
            sb.append(" - via Dependency Locking");
        }

        return sb.toString();
    }
}
