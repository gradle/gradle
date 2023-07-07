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
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nonnull;
import java.util.Collection;

import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderSolutionsWithNewProblemsApi;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class DefaultCatalogProblemBuilder {

    private final static DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    public static final String VERSION_CATALOG_PROBLEMS = "version_catalog_problems";

    public static void maybeThrowError(String error, Collection<Problem> problems, Problems problemService) {
        if (!problems.isEmpty()) {
            throw throwErrorWithNewProblemsApi(error, problems, problemService);
        }
    }

    public static RuntimeException throwErrorWithNewProblemsApi(String error, Collection<Problem> problems, Problems problemService) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(error);
        formatter.startChildren();
        for (Problem problem : problems) {
            reportInto(formatter, problem);
        }
        formatter.endChildren();

        problemService.collectErrors(problems);
        throw new InvalidUserDataException(formatter.toString());
    }

    private static void reportInto(TreeFormatter output, Problem problem) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(problem.getMessage());
        if (problem.getDescription() != null) {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(problem.getDescription())));
        }

        renderSolutionsWithNewProblemsApi(formatter, problem.getSolutions());
        DocLink documentationLink = problem.getDocumentationLink();
        if (documentationLink != null) {
            formatter.blankLine();
            formatter.node(DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", documentationLink));
        }
        output.node(formatter.toString());
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
