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
package org.gradle.api.internal.catalog.problems;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderSolutions;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class DefaultCatalogProblemBuilder {

    private final static DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    public static final String VERSION_CATALOG_PROBLEMS = "version_catalog_problems";

    public static void maybeThrowError(InternalProblems problemsService, String error, Collection<Problem> problems) {
        if (!problems.isEmpty()) {
            throw throwError(problemsService, error, problems);
        }
    }

    public static RuntimeException throwError(InternalProblems problemsService, String error, Collection<Problem> problems) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(error);
        formatter.startChildren();
        for (Problem problem : problems) {
            formatter.node(getProblemString(problem));
            problemsService.getReporter().report(problem);
        }
        formatter.endChildren();
        throw new InvalidUserDataException(formatter.toString());
    }

    public static String getProblemString(Problem problem) {
        ProblemDefinition definition = problem.getDefinition();
        String contextualLabel = problem.getContextualLabel();
        String renderedLabel = contextualLabel == null ? definition.getId().getDisplayName() : contextualLabel;
        return getProblemString(renderedLabel, problem.getDetails(), problem.getSolutions(), definition.getDocumentationLink());
    }

    public static String getProblemString(String label, String details, List<String> solutions, DocLink documentationLink) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(label);
        if (details != null) {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(details)));
        }

        renderSolutions(formatter, solutions);
        if (documentationLink != null) {
            formatter.blankLine();
            formatter.node(DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", documentationLink));
        }
        return formatter.toString();
    }

    @Nonnull
    public static String getProblemInVersionCatalog(VersionCatalogBuilder builder) {
        return getProblemInVersionCatalog(builder.getName());
    }

    @Nonnull
    public static String getProblemInVersionCatalog(String name) {
        return "Problem: " + getInVersionCatalog(name);
    }

    @Nonnull
    public static String getInVersionCatalog(String name) {
        return "In version catalog " + name;
    }
}
