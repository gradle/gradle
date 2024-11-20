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

package org.gradle.problems.internal.rendering;

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NonNullApi
public class ProblemRenderer {

    private final PrintWriter output;

    public ProblemRenderer(Writer writer) {
        output = new PrintWriter(writer);
    }

    public void render(List<Problem> problems) {
        Map<ProblemId, List<Problem>> renderingGroups = new HashMap<>();
        for (Problem problem : problems) {
            List<Problem> groupedProblems = renderingGroups.computeIfAbsent(
                problem.getDefinition().getId(),
                id -> new ArrayList<>()
            );
            groupedProblems.add(problem);
        }

        renderingGroups.forEach((id, groupedProblems) -> renderProblemGroup(output, id, groupedProblems));
    }

    public void render(Problem problem) {
        this.render(Collections.singletonList(problem));
    }

    static void renderProblemGroup(PrintWriter output, ProblemId id, List<Problem> groupedProblems) {
        groupedProblems.forEach(problem -> renderProblem(output, problem));
    }

    static void renderProblem(PrintWriter output, Problem problem) {
        boolean isJavaCompilationProblem = problem.getDefinition().getId().getGroup().equals(GradleCoreProblemGroup.compilation().java());
        if (isJavaCompilationProblem) {
            formatMultiline(output, problem.getDetails(), 0);
        } else {
            if (problem.getContextualLabel() != null) {
                formatMultiline(output, problem.getContextualLabel(), 1);
            } else {
                formatMultiline(output, problem.getDefinition().getId().getDisplayName(), 1);
            }
            if (problem.getDetails() != null) {
                formatMultiline(output, problem.getDetails(), 2);
            }
        }
    }

    static void formatMultiline(PrintWriter output, String message, int level) {
        if (message == null) {
            return;
        }
        for (String line : message.split("\n")) {
            for (int i = 0; i < level; i++) {
                output.print("  ");
            }
            output.printf("%s%n", line);
        }
    }
}
