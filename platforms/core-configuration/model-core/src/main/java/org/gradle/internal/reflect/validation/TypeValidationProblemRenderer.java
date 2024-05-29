/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.internal.Problem;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class TypeValidationProblemRenderer {

    public static String renderMinimalInformationAbout(Problem problem) {
        return renderMinimalInformationAbout(problem, true);
    }

    public static String renderMinimalInformationAbout(Problem problem, boolean renderDocLink) {
        return renderMinimalInformationAbout(problem, renderDocLink, true);
    }

    public static String renderMinimalInformationAbout(Problem problem, boolean renderDocLink, boolean renderSolutions) {
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
