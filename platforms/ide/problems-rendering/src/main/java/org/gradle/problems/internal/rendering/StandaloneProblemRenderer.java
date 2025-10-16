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

package org.gradle.problems.internal.rendering;

import com.google.common.base.Strings;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.LineInFileLocation;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

public class StandaloneProblemRenderer extends ProblemRenderer {

    private final String prefix;

    StandaloneProblemRenderer(Writer output) {
        this(output, "Problem found: ");
    }
    StandaloneProblemRenderer(Writer output, String prefix) {
        super(output);
        this.prefix = prefix;
    }

    public void render(InternalProblem problem) {
        // first line: generic message rendering the problem categor
        indent(output, problemHeaderMessage(problem), 0);

        // second line: contextual message, if any
        String problemSubMessage = getContextualMessage(problem);
        if (problemSubMessage != null) {
            output.printf("%n");
            indent(output, problemSubMessage, 2);
        }

        // indent details further if there was a contextual message
        if (problem.getDetails() != null) {
            output.printf("%n");
            indent(output, problem.getDetails(), problemSubMessage == null ? 2 : 4);
        }

        // print solutions
        if (!problem.getSolutions().isEmpty()) {
            for (String solution : problem.getSolutions()) {
                output.printf("%n");

                String[] lines = solution.split("\n");
                indent(output, "Solution: " + lines[0], 4);
                for (int i = 1; i < lines.length; i++) {
                    output.printf("%n");
                    indent(output, lines[i], 14);
                }
            }
        }

        // print locations
        List<FileLocation> fileLocations = problem.getOriginLocations().stream().filter(FileLocation.class::isInstance).map(FileLocation.class::cast).collect(Collectors.toList());
        for (FileLocation location : fileLocations) {
            output.printf("%n");
            indent(output, "Location: " + location.getPath(), 4);
            if (location instanceof LineInFileLocation) {
                LineInFileLocation lineLocation = (LineInFileLocation) location;
                output.printf(" line " + lineLocation.getLine());
            }
        }
    }

    private String problemHeaderMessage(InternalProblem problem) {
        StringBuilder result = new StringBuilder(prefix);
        String displayName = problem.getDefinition().getId().getDisplayName();
        String name = (displayName == null || displayName.isEmpty()) ? problem.getDefinition().getId().toString() : displayName;
        name = name.replaceAll("[\\r\\n]+", " ");
        result.append(name);
        if (!(displayName == null || displayName.isEmpty())) {
            result.append(" (id: ");
            result.append(problem.getDefinition().getId());
            result.append(")");
        }
        result.append("\n");
        return result.toString();
    }

    @Nullable
    private static String getContextualMessage(InternalProblem problem) {
        if (problem.getContextualLabel() != null) {
            return problem.getContextualLabel();
        } else if (problem.getException() != null) {
            return problem.getException().getLocalizedMessage();
        }
        return null;
    }

    static void indent(PrintWriter output, @Nullable String message, int level) {
        if (message == null) {
            return;
        }
        @SuppressWarnings("InlineMeInliner")
        String prefix = Strings.repeat(" ", level);
        String formatted = TextUtil.indent(message, prefix);
        output.print(formatted);
    }

}
