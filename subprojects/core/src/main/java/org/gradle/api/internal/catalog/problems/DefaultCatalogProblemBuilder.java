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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.problems.Solution;
import org.gradle.problems.StandardSeverity;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderSolutionsWithNewProblemsApi;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class DefaultCatalogProblemBuilder implements VersionCatalogProblemBuilder,
    VersionCatalogProblemBuilder.ProblemWithId,
    VersionCatalogProblemBuilder.DescribedProblem,
    VersionCatalogProblemBuilder.DescribedProblemWithCause {

    private final static DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    private final VersionCatalogProblemId id;
    private Supplier<String> context;
    private Supplier<String> shortDescription;
    private Supplier<String> longDescription = () -> "";
    private Supplier<String> reason;
    private StandardSeverity severity = StandardSeverity.ERROR;
    private final List<Supplier<String>> solutions = Lists.newArrayListWithExpectedSize(1);
    private DocLink docLink;

    private DefaultCatalogProblemBuilder(VersionCatalogProblemId id) {
        this.id = id;
    }

    public static VersionCatalogProblem buildProblem(VersionCatalogProblemId id, Consumer<? super VersionCatalogProblemBuilder> spec) {
        DefaultCatalogProblemBuilder builder = new DefaultCatalogProblemBuilder(id);
        spec.accept(builder);
        return builder.build();
    }

    public static void maybeThrowError(String error, List<VersionCatalogProblem> problems) {
        if (!problems.isEmpty()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(error);
            formatter.startChildren();
            for (VersionCatalogProblem problem : problems) {
                problem.reportInto(formatter);
            }
            formatter.endChildren();
            throw new InvalidUserDataException(formatter.toString());
        }
    }

    public static RuntimeException throwErrorWithNewProblemsApi(String error, List<Problem> problems) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(error);
        formatter.startChildren();
        for (Problem problem : problems) {
            reportInto(formatter, problem);
        }
        formatter.endChildren();
        throw Problems.throwing(problems, new InvalidUserDataException(formatter.toString()));
//            throw new InvalidUserDataException(formatter.toString());
    }

    private static void reportInto(TreeFormatter output, Problem problem) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(problem.getMessage());
        if (problem.getDescription() != null) {
            formatter.blankLine();
            formatter.node("Reason: " + capitalize(endLineWithDot(problem.getDescription())));
        }

        renderSolutionsWithNewProblemsApi(formatter, problem.getSolutions());
        if (problem.getDocumentationLink() != null) {
            formatter.blankLine();
            formatter.node(problem.getDocumentationLink());
        }
        output.node(formatter.toString());
    }

    @Override
    public ProblemWithId inContext(Supplier<String> context) {
        this.context = context;
        return this;
    }

    @Override
    public ProblemWithId withSeverity(StandardSeverity severity) {
        this.severity = severity;
        return this;
    }

    @Override
    public DescribedProblem withShortDescription(Supplier<String> description) {
        this.shortDescription = description;
        return this;
    }

    @Override
    public DescribedProblem withLongDescription(Supplier<String> description) {
        this.longDescription = description;
        return this;
    }

    @Override
    public DescribedProblemWithCause happensBecause(Supplier<String> reason) {
        this.reason = reason;
        return this;
    }

    @Override
    public DescribedProblemWithCause documentedAt(String page, String section) {
        this.docLink = new DocLink(page, section);
        return this;
    }

    @Override
    public DescribedProblemWithCause documented() {
        return documentedAt("version_catalog_problems", id.name().toLowerCase());
    }

    @Override
    public DescribedProblemWithCause addSolution(Supplier<String> solution) {
        solutions.add(solution);
        return this;
    }

    public VersionCatalogProblem build() {
        if (context == null) {
            throw new IllegalStateException("You must provide the context of this problem");
        }
        if (shortDescription == null) {
            throw new IllegalStateException("You must provide a short description of the problem");
        }
        if (reason == null) {
            throw new IllegalStateException("You must provide the reason why this problem happened");
        }
        if (solutions.isEmpty()) {
            throw new IllegalStateException("You must provide at least one solution to the problem");
        }
        return new VersionCatalogProblem(
            id,
            severity,
            context.get(),
            shortDescription,
            longDescription,
            reason,
            () -> docLink == null ? null : DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", docLink.page, docLink.section),
            solutions.stream().map(this::toSolution).collect(toList())
        );
    }

    private Supplier<Solution> toSolution(Supplier<String> solutionText) {
        return () -> new SimpleSolution(solutionText);
    }

    private static class DocLink {
        private final String page;
        private final String section;

        private DocLink(String page, String section) {
            this.page = page;
            this.section = section;
        }
    }

    private static class SimpleSolution implements Solution {
        private final Supplier<String> descriptor;

        private SimpleSolution(Supplier<String> descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String getShortDescription() {
            return descriptor.get();
        }
    }
}
