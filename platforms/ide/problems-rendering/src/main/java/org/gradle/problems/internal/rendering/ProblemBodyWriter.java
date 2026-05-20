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
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.LineInFileLocation;
import org.gradle.api.problems.internal.DocLinkInternal;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes the 'body' of a problem, i.e., details, solutions, and locations.
 * The remaining rendering is implemented in {@link ProblemHeaderWriter}.
 */
class ProblemBodyWriter implements PartialProblemWriter {

    private static final int LEVEL_1_INDENT = 2;
    private static final int LEVEL_2_INDENT = 4;
    private static final int LEVEL_3_INDENT = 6;

    @Override
    public void write(ProblemInternal problem, RenderOptions options, PrintWriter output) {
        // contextual message, if any
        String problemSubMessage = getContextualMessage(problem);
        if (problemSubMessage != null) {
            output.printf("%n");
            indent(output, problemSubMessage, LEVEL_1_INDENT);
        }

        // indent details further if there was a contextual message
        if (problem.getDetails() != null) {
            output.printf("%n");
            indent(output, problem.getDetails(), problemSubMessage == null ? LEVEL_1_INDENT : LEVEL_2_INDENT);
        }

        // link to documentation
        DocLink documentationLink = problem.getDefinition().getDocumentationLink();
        if (documentationLink != null && documentationLink.getUrl() != null) {
            output.printf("%n");
            String message = documentationLink instanceof DocLinkInternal
                ? ((DocLinkInternal) documentationLink).getConsultDocumentationMessage()
                : String.format("For more information, please refer to %s.", documentationLink.getUrl());
            indent(output, message, LEVEL_2_INDENT);
        }

        // locations
        List<FileLocation> fileLocations = problem.getOriginLocations().stream().filter(FileLocation.class::isInstance).map(FileLocation.class::cast).collect(Collectors.toList());
        for (FileLocation location : fileLocations) {
            output.printf("%n");
            indent(output, "Location: " + location.getPath(), LEVEL_2_INDENT);
            if (location instanceof LineInFileLocation) {
                LineInFileLocation lineLocation = (LineInFileLocation) location;
                output.printf(" line " + lineLocation.getLine());
            }
        }

        // solutions
        List<String> solutions = problem.getSolutions();
        if (!solutions.isEmpty()) {
            output.printf("%n");
            if (solutions.size() == 1) {
                writePrefixedMultiline(output, "Possible solution: ", LEVEL_2_INDENT, normalize(solutions.get(0)));
            } else {
                indent(output, "Possible solutions:", LEVEL_2_INDENT);
                for (int i = 0; i < solutions.size(); i++) {
                    output.printf("%n");
                    writePrefixedMultiline(output, (i + 1) + ". ", LEVEL_3_INDENT, normalize(solutions.get(i)));
                }
            }
        }
    }

    private static String normalize(String message) {
        return TextUtil.capitalize(TextUtil.endLineWithDot(message));
    }

    private static void writePrefixedMultiline(PrintWriter output, String firstLinePrefix, int firstLineIndent, String text) {
        String[] lines = text.split("\\r?\\n");
        indent(output, firstLinePrefix + lines[0], firstLineIndent);
        int continuationIndent = firstLineIndent + firstLinePrefix.length();
        for (int i = 1; i < lines.length; i++) {
            output.printf("%n");
            indent(output, lines[i], continuationIndent);
        }
    }

    @Nullable
    private static String getContextualMessage(ProblemInternal problem) {
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
