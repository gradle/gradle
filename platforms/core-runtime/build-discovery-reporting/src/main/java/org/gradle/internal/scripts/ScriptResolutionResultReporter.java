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

package org.gradle.internal.scripts;

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;

import java.io.File;
import java.util.stream.Collectors;

public class ScriptResolutionResultReporter {

    private final ProblemReporter problemReporter;

    public ScriptResolutionResultReporter(ProblemReporter problemReporter) {
        this.problemReporter = problemReporter;
    }

    public void reportResolutionProblemsOf(ScriptResolutionResult result) {
        if (result.getSelectedCandidate() == null || result.getIgnoredCandidates().isEmpty()) {
            return;
        }

        String ignoredCandidateList = result.getIgnoredCandidates()
            .stream()
            .map(File::getName)
            .map(name -> "'" + name + "'")
            .collect(Collectors.joining(", "));

        problemReporter.report(
            ProblemId.create("multiple-scripts", "Multiple scripts", GradleCoreProblemGroup.scripts()),
            spec -> spec.contextualLabel(
                String.format("Multiple %s script files were found in directory '%s'", result.getBasename(), result.getDirectory())
            ).details(
                String.format(
                    "Multiple %s script files were found in directory '%s'. Selected '%s', and ignoring %s.",
                    result.getBasename(),
                    result.getDirectory(),
                    result.getSelectedCandidate().getName(),
                    ignoredCandidateList
                )
            ).solution(
                String.format(
                    "Delete the files %s in directory '%s'",
                    ignoredCandidateList,
                    result.getDirectory()
                )
            )
        );
    }

}
