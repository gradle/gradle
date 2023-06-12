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

import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.model.internal.type.ModelType;
import org.gradle.plugin.use.PluginId;
import org.gradle.problems.Solution;

import java.util.List;

import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class TypeValidationProblemRenderer {

    // We should get rid of this method quickly, as the validation message
    // system was designed to display the same message unconditionally
    // whatever asks for it, when it should be the responisiblity of the
    // consumer to render it as they need. For example, an HTML renderer
    // may use the same information differently
    public static String renderMinimalInformationAbout(TypeValidationProblem problem) {
        return renderMinimalInformationAbout(problem, true);
    }

    public static String renderMinimalInformationAbout(TypeValidationProblem problem, boolean renderDocLink) {
        return renderMinimalInformationAbout(problem, renderDocLink, true);
    }

    public static String renderMinimalInformationAbout(TypeValidationProblem problem, boolean renderDocLink, boolean renderSolutions) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(introductionFor(problem.getWhere()) + endLineWithDot(problem.getShortDescription()));
        problem.getWhy().ifPresent(reason -> {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(reason)));
        });
        if (renderSolutions) {
            renderSolutions(formatter, problem.getPossibleSolutions());
        }
        if (renderDocLink) {
            problem.getDocumentationLink().ifPresent(docLink -> {
                formatter.blankLine();
                formatter.node(docLink);
            });
        }
        return formatter.toString();
    }

    public static void renderSolutions(TreeFormatter formatter, List<Solution> possibleSolutions) {
        int solutionCount = possibleSolutions.size();
        if (solutionCount > 0) {
            formatter.blankLine();
            if (solutionCount == 1) {
                formatter.node("Possible solution: " + capitalize(endLineWithDot(possibleSolutions.get(0).getShortDescription())));
            } else {
                formatter.node("Possible solutions");
                formatter.startNumberedChildren();
                possibleSolutions.forEach(solution ->
                    formatter.node(capitalize(endLineWithDot(solution.getShortDescription())))
                );
                formatter.endChildren();
            }
        }
    }

    public static void renderSolutionsWithNewProblemsApi(TreeFormatter formatter, List<String> possibleSolutions) {
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

    private static String introductionFor(TypeValidationProblemLocation location) {
        StringBuilder builder = new StringBuilder();
        Class<?> rootType = location.getType()
            .filter(TypeValidationProblemRenderer::shouldRenderType)
            .orElse(null);
        PluginId pluginId = location.getPlugin().orElse(null);
        if (rootType != null) {
            if (pluginId != null) {
                builder.append("In plugin '").append(pluginId).append("' type '");
            } else {
                builder.append("Type '");
            }
            builder.append(ModelType.of(rootType).getName())
                .append("' ");
        }
        String property = location.getPropertyName().orElse(null);
        if (property != null) {
            if (rootType == null) {
                if (pluginId != null) {
                    builder.append("In plugin '").append(pluginId).append("' property '");
                } else {
                    builder.append("Property '");
                }
            } else {
                builder.append("property '");
            }
            location.getParentPropertyName().ifPresent(parentProperty -> {
                builder.append(parentProperty);
                builder.append('.');
            });
            builder.append(property)
                .append("' ");
        }
        return builder.toString();
    }

    // A heuristic to determine if the type is relevant or not.
    // The "DefaultTask" type may appear in error messages
    // (if using "adhoc" tasks) but isn't visible to this
    // class so we have to rely on text matching for now.
    private static boolean shouldRenderType(Class<?> clazz) {
        return !("org.gradle.api.DefaultTask".equals(clazz.getName()));
    }
}
