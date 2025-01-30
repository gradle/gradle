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
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.util.internal.TextUtil;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

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
        String sep = "";
        for (InternalProblem problem : problems) {
            output.printf(sep);
            renderProblem(output, problem);
            sep = "%n";
        }
    }

    static void renderProblem(PrintWriter output, InternalProblem problem) {
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
                output.printf("%n");
                formatMultiline(output, problem.getDetails(), 2);
            }
        }
    }

    static void formatMultiline(PrintWriter output, String message, int level) {
        if (message == null) {
            return;
        }
        @SuppressWarnings("InlineMeInliner")
        String prefix = Strings.repeat(" ", level * 2);
        String formatted = TextUtil.indent(message, prefix);
        output.print(formatted);
    }
}
