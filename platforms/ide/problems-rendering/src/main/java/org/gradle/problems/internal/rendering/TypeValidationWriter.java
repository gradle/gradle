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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.internal.logging.text.TreeFormatter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.gradle.util.internal.TextUtil.capitalize;
import static org.gradle.util.internal.TextUtil.endLineWithDot;


class TypeValidationWriter implements SelectiveProblemWriter {

    @Override
    public void write(InternalProblem problem, RenderOptions options, PrintWriter output) {
        output.write(renderMinimalInformationAbout(problem, true, true));
    }

    @Override
    public boolean accepts(ProblemId problemId) {
        return problemId.getGroup().equals(GradleCoreProblemGroup.validation().type()) || problemId.getGroup().equals(GradleCoreProblemGroup.validation().property());
    }

    // TODO (donat) copied from TypeValidationProblemRenderer. We should not have any duplication.
    private static String renderMinimalInformationAbout(InternalProblem problem, boolean renderDocLink, boolean renderSolutions) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(endLineWithDot(Optional.ofNullable(problem.getContextualLabel()).orElseGet(() -> problem.getDefinition().getId().getDisplayName())));
        ofNullable(problem.getDetails()).ifPresent(reason -> {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(problem.getDetails())));
        });
        if (renderSolutions) {
            List<String> allSolutions = new ArrayList<>(problem.getSolutions().size() + problem.getSolutions().size());
            allSolutions.addAll(problem.getSolutions());
            renderSolutions(formatter, allSolutions);
        }
        if (renderDocLink) {
            ofNullable(problem.getDefinition().getDocumentationLink()).ifPresent(docLink -> {
                formatter.blankLine();
                formatter.node(new DocumentationRegistry().getDocumentationRecommendationFor("information", docLink));
            });
        }
        return formatter.toString();
    }

    public static void renderSolutions(TreeFormatter formatter, List<String> possibleSolutions) {
        int solutionCount = possibleSolutions.size();
        if (solutionCount > 0) {
            formatter.blankLine();
            if (solutionCount == 1) {
                formatter.node("Possible solution: " + capitalize(endLineWithDot(possibleSolutions.get(0))));
            } else {
                formatter.node("Possible solutions");
                formatter.startNumberedChildren();
                possibleSolutions.forEach(solution ->
                    formatter.node(capitalize(endLineWithDot(solution)))
                );
                formatter.endChildren();
            }
        }
    }

    /**
     * This is an adhoc reformatting tool which should go away as soon as we have
     * a better way to display multiline deprecation warnings
     */
    public static String convertToSingleLine(String message) {
        return message.replaceAll("(\\r?\\n *)+", ". ")
            .replaceAll("[.]+", ".")
            .replaceAll("[ ]+", " ")
            .replaceAll(": ?[. ]", ": ");
    }
}
