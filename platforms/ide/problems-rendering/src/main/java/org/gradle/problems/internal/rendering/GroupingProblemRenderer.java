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

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupingProblemRenderer {

    private final HeaderRenderer headerRenderer;
    private final BodyRenderer bodyRenderer;
    private final PrintWriter output;

    GroupingProblemRenderer(HeaderRenderer headerRenderer, BodyRenderer bodyRenderer, PrintWriter output) {
        this.headerRenderer = headerRenderer;
        this.bodyRenderer = bodyRenderer;
        this.output = output;
    }

    public void render(List<InternalProblem> problems) {
        render(output, problems);
    }

    public void render(InternalProblem problem) {
        render(Collections.singletonList(problem));
    }

    private void render(PrintWriter output, List<InternalProblem> problems) {
        // Group problems by problem id
        // When generic rendering is addressed, maybe we also group by the whole problem group hierarchy
        Map<ProblemId, List<InternalProblem>> problemIdListMap = problems.stream().collect(Collectors.groupingBy(internalProblem -> internalProblem.getDefinition().getId()));
        String separator = "";
        for (Map.Entry<ProblemId, List<InternalProblem>> problemIdListEntry : problemIdListMap.entrySet()) {
            renderProblemsById(output, problemIdListEntry.getKey(), problemIdListEntry.getValue(), separator);
            separator = "%n";
        }
    }

    private void renderProblemsById(PrintWriter output, ProblemId problemId, List<InternalProblem> problems, String separator) {
        String sep = separator;
        boolean isJavaCompilationProblem = problemId.getGroup().equals(GradleCoreProblemGroup.compilation().java()) && !problemId.getName().equals("initialization-failed");
        if (isJavaCompilationProblem) {
            for (InternalProblem problem : problems) {
                output.printf(sep);
                renderJavaCompilationProblem(output, problem);
                sep = "%n";
            }
        } else {
            for (InternalProblem problem : problems) {
                output.printf(sep);
                headerRenderer.render(problem, output);
                bodyRenderer.render(problem, output);
                sep = "%n";
            }
        }
    }

    static void renderJavaCompilationProblem(PrintWriter output, InternalProblem problem) {
        output.print(problem.getDetails());
    }
}
