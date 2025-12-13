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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.internal.component.resolution.failure.SelectionReasonAssessor.AssessedSelection.AssessedSelectionReason;
import org.gradle.internal.component.resolution.failure.exception.ConflictingConstraintsException;
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure;
import org.gradle.util.internal.VersionNumber;

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
        List<AssessedSelectionReason> versionsByReason = findConflictingConstraints(failure);
        int uniqueVersions = versionsByReason.stream()
            .map(AssessedSelectionReason::getRequiredVersion)
            .collect(Collectors.toSet())
            .size();
        return uniqueVersions > 1;
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
        Multimap<VersionNumber, String> conflictingVersionsWithExplanations = HashMultimap.create();
        findConflictingConstraints(failure).forEach(v -> {
            VersionNumber version = VersionNumber.parse(v.getRequiredVersion());
            String explanation = explainReason(v);
            conflictingVersionsWithExplanations.put(version, explanation);
        });

        StringBuilder sb = new StringBuilder("Component is the target of multiple version constraints with conflicting requirements:\n");
        conflictingVersionsWithExplanations.keySet().stream().sorted().forEach(version -> {
            List<String> explanations = conflictingVersionsWithExplanations.get(version).stream().sorted().collect(Collectors.toList());
            sb.append(explanations.get(0));
            int numOtherPaths = explanations.size() -1;
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

    private String explainReason(AssessedSelectionReason reason) {
        StringBuilder sb = new StringBuilder(reason.getRequiredVersion());

        if (reason.isFromLock()) {
            sb.append(" - from lock file");
        } else {
            // Size == 1 means the constraint is declared in the root project, no need for "via"
            int pathLength = reason.getSegmentedSelectionPath().size();
            if (pathLength == 2) { // The constraint is declared in a direct dependency
                sb.append(" - directly in ").append(reason.getSegmentedSelectionPath().get(1));
            } else if (pathLength > 2) { // The constraint is declared at some arbitrarily deep point in the graph
                sb.append(" - transitively via ").append(reason.getSegmentedSelectionPath().get(1));
            }
        }

        return sb.toString();
    }
}
