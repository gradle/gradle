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

import com.google.common.base.Strings;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NonNullApi
public class ProblemRenderer {

    private final PrintWriter output;

    public ProblemRenderer(Writer writer) {
        output = new PrintWriter(writer);
    }

    public void render(List<InternalProblem> problems) {
        render(output, problems);
    }

    public void render(InternalProblem problem) {
        render(Collections.singletonList(problem));
    }

    private static void render(PrintWriter output, List<InternalProblem> problems) {
        // Group problems by problem id
        // When generic rendering is addressed, maybe we also group by the whole problem group hierarchy
        Map<ProblemId, List<InternalProblem>> problemIdListMap = problems.stream().collect(Collectors.groupingBy(internalProblem -> internalProblem.getDefinition().getId()));
        String separator = "";
        for (Map.Entry<ProblemId, List<InternalProblem>> problemIdListEntry : problemIdListMap.entrySet()) {
            renderProblemsById(output, problemIdListEntry.getKey(), problemIdListEntry.getValue(), separator);
            separator = "%n";
        }
    }

    private static void renderProblemsById(PrintWriter output, ProblemId problemId, List<InternalProblem> problems, String separator) {
        String sep = separator;
        boolean isJavaCompilationProblem = problemId.getGroup().equals(GradleCoreProblemGroup.compilation().java()) && !problemId.getName().equals("initialization-failed");
        if (isJavaCompilationProblem) {
            for (InternalProblem problem : problems) {
                output.printf(sep);
                renderJavaCompilationProblem(output, problem);
                sep = "%n";
            }
        } else {
            output.printf(sep);
            sep = "%n";
            formatMultiline(output, problemId.getDisplayName(), 0);
            for (InternalProblem problem : problems) {
                output.printf(sep);
                renderProblem(output, problem);
            }
        }
    }

    static void renderProblem(PrintWriter output, InternalProblem problem) {
        formatMultiline(output, getProblemLabel(problem), 1);
        if (problem.getDetails() != null) {
            output.printf("%n");
            formatMultiline(output, problem.getDetails(), 2);
        }
    }

    @Nullable
    private static String getProblemLabel(InternalProblem problem) {
        if (problem.getContextualLabel() != null) {
            return problem.getContextualLabel();
        } else if (problem.getException() != null) {
            return problem.getException().getLocalizedMessage();
        } else if (problem.getDetails() != null) {
            return "Unlabelled problem details:";
        }
        return null;
    }

    static void renderJavaCompilationProblem(PrintWriter output, InternalProblem problem) {
        formatMultiline(output, problem.getDetails(), 0);
    }

    static void formatMultiline(PrintWriter output, @Nullable String message, int level) {
        if (message == null) {
            return;
        }
        @SuppressWarnings("InlineMeInliner")
        String prefix = Strings.repeat(" ", level * 2);
        String formatted = TextUtil.indent(message, prefix);
        output.print(formatted);
    }
}
