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
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;
import static org.gradle.util.internal.TextUtil.capitalize;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

class ValidationWriter {

    public static String renderMinimalInformationAbout(InternalProblem problem) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(endLineWithDot(ofNullable(problem.getContextualLabel()).orElseGet(() -> problem.getDefinition().getId().getDisplayName())));
        ofNullable(problem.getDetails()).ifPresent(reason -> {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(problem.getDetails())));
        });

        List<String> allSolutions = new ArrayList<>(problem.getSolutions().size() + problem.getSolutions().size());
        allSolutions.addAll(problem.getSolutions());
        renderSolutions(formatter, allSolutions);

        ofNullable(problem.getDefinition().getDocumentationLink()).ifPresent(docLink -> {
            formatter.blankLine();
            formatter.node(new DocumentationRegistry().getDocumentationRecommendationFor("information", docLink));
        });
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
}
