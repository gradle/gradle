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
import org.gradle.api.problems.Problem;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;
import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.internal.reflect.validation.DefaultTypeAwareProblemBuilder.PARENT_PROPERTY_NAME;
import static org.gradle.internal.reflect.validation.DefaultTypeAwareProblemBuilder.PLUGIN_ID;
import static org.gradle.internal.reflect.validation.DefaultTypeAwareProblemBuilder.PROPERTY_NAME;
import static org.gradle.internal.reflect.validation.DefaultTypeAwareProblemBuilder.TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE;
import static org.gradle.internal.reflect.validation.DefaultTypeAwareProblemBuilder.TYPE_NAME;
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
        formatter.node(introductionFor(problem.getAdditionalData()) + endLineWithDot(problem.getLabel()));
        ofNullable(problem.getDetails()).ifPresent(reason -> {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(problem.getDetails())));
        });
        if (renderSolutions) {
            renderSolutionsWithNewProblemsApi(formatter, problem.getSolutions());
        }
        if (renderDocLink) {
            ofNullable(problem.getDocumentationLink()).ifPresent(docLink -> {
                formatter.blankLine();
                formatter.node(new DocumentationRegistry().getDocumentationRecommendationFor("information", docLink));
            });
        }
        return formatter.toString();
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

    public static String introductionFor(Map<String, String> additionalMetadata) {
        StringBuilder builder = new StringBuilder();
        String rootType = ofNullable(additionalMetadata.get(TYPE_NAME))
            .filter(TypeValidationProblemRenderer::shouldRenderType)
            .orElse(null);
        DefaultPluginId pluginId = ofNullable(additionalMetadata.get(PLUGIN_ID)).map(DefaultPluginId::new).orElse(null);
        boolean typeRelevant = rootType != null && !parseBoolean(additionalMetadata.get(TYPE_IS_IRRELEVANT_IN_ERROR_MESSAGE));
        if (typeRelevant) {
            if (pluginId != null) {
                builder.append("In plugin '")
                    .append(pluginId)
                    .append("' type '");
            } else {
                builder.append("Type '");
            }
            builder.append(rootType).append("' ");
        }

        String property = additionalMetadata.get(PROPERTY_NAME);
        if (property != null) {
            if (typeRelevant) {
                builder.append("property '");
            } else {
                if (pluginId != null) {
                    builder.append("In plugin '")
                        .append(pluginId)
                        .append("' property '");
                } else {
                    builder.append("Property '");
                }
            }
            ofNullable(additionalMetadata.get(PARENT_PROPERTY_NAME)).ifPresent(parentProperty -> {
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
    private static boolean shouldRenderType(String className) {
        return !"org.gradle.api.DefaultTask".equals(className);
    }
}
