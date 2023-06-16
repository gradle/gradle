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
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nonnull;
import java.util.Collection;

import static org.apache.commons.lang.StringUtils.capitalize;
import static org.gradle.api.problems.interfaces.ProblemGroup.VERSION_CATALOG;
import static org.gradle.api.problems.interfaces.Severity.ERROR;
import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.renderSolutionsWithNewProblemsApi;
import static org.gradle.util.internal.TextUtil.endLineWithDot;

public class DefaultCatalogProblemBuilder {
//    implements VersionCatalogProblemBuilder,
//    VersionCatalogProblemBuilder.ProblemWithId,
//    VersionCatalogProblemBuilder.DescribedProblem,
//    VersionCatalogProblemBuilder.DescribedProblemWithCause
//{

    private final static DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    public static final String VERSION_CATALOG_PROBLEMS = "version_catalog_problems";

//    private final VersionCatalogProblemId id;
//    private Supplier<String> context;
//    private Supplier<String> shortDescription;
//    private Supplier<String> longDescription = () -> "";
//    private Supplier<String> reason;
//    private StandardSeverity severity = StandardSeverity.ERROR;
//    private final List<Supplier<String>> solutions = Lists.newArrayListWithExpectedSize(1);
//    private DefaultDocLink docLink;

//    private DefaultCatalogProblemBuilder(VersionCatalogProblemId id) {
//        this.id = id;
//    }

//    public static VersionCatalogProblem buildProblem(VersionCatalogProblemId id, Consumer<? super VersionCatalogProblemBuilder> spec) {
//        DefaultCatalogProblemBuilder builder = new DefaultCatalogProblemBuilder(id);
//        spec.accept(builder);
//        return builder.build();
//    }

//    public static void maybeThrowError(String error, List<VersionCatalogProblem> problems) {
//        if (!problems.isEmpty()) {
//            TreeFormatter formatter = new TreeFormatter();
//            formatter.node(error);
//            formatter.startChildren();
//            for (VersionCatalogProblem problem : problems) {
//                problem.reportInto(formatter);
//            }
//            formatter.endChildren();
//            throw new InvalidUserDataException(formatter.toString());
//        }
//    }

    public static void maybeThrowError(String error, Collection<Problem> problems) {
        if (!problems.isEmpty()) {
            throw throwErrorWithNewProblemsApi(error, problems);
        }
    }

    public static RuntimeException throwErrorWithNewProblemsApi(String error, Collection<Problem> problems) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(error);
        formatter.startChildren();
        for (Problem problem : problems) {
            reportInto(formatter, problem);
        }
        formatter.endChildren();

        Problems.collect(problems);
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
            formatter.node(DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", documentationLink.getPage(), documentationLink.getSection()));
        }
        output.node(formatter.toString());
    }

    @Nonnull
    public static String getProblemInVersionCatalog(VersionCatalogBuilder builder) {
        return getProblemInVersionCatalog(builder.getName());
    }

    @Nonnull
    public static String getProblemInVersionCatalog(String name) {
        return "Problem: In version catalog " + name;
    }

    @Nonnull
    public static ProblemBuilder createVersionCatalogError(String message, VersionCatalogProblemId catalogProblemId) {
        return Problems.createNew(VERSION_CATALOG, message, ERROR)
            .type(catalogProblemId.name())
            .documentedAt(VERSION_CATALOG_PROBLEMS, catalogProblemId.name().toLowerCase());
    }

//    @Override
//    public ProblemWithId inContext(Supplier<String> context) {
//        this.context = context;
//        return this;
//    }

//    @Override
//    public ProblemWithId withSeverity(StandardSeverity severity) {
//        this.severity = severity;
//        return this;
//    }
//
//    @Override
//    public DescribedProblem withShortDescription(Supplier<String> description) {
//        this.shortDescription = description;
//        return this;
//    }

//    @Override
//    public DescribedProblem withLongDescription(Supplier<String> description) {
//        this.longDescription = description;
//        return this;
//    }

//    @Override
//    public DescribedProblemWithCause happensBecause(Supplier<String> reason) {
//        this.reason = reason;
//        return this;
//    }
//
//    @Override
//    public DescribedProblemWithCause documentedAt(String page, String section) {
//        this.docLink = new DefaultDocLink(page, section);
//        return this;
//    }

//    @Override
//    public DescribedProblemWithCause documented() {
//        return documentedAt(VERSION_CATALOG_PROBLEMS, id.name().toLowerCase());
//    }

//    @Override
//    public DescribedProblemWithCause addSolution(Supplier<String> solution) {
//        solutions.add(solution);
//        return this;
//    }

//    public VersionCatalogProblem build() {
//        if (context == null) {
//            throw new IllegalStateException("You must provide the context of this problem");
//        }
//        if (shortDescription == null) {
//            throw new IllegalStateException("You must provide a short description of the problem");
//        }
//        if (reason == null) {
//            throw new IllegalStateException("You must provide the reason why this problem happened");
//        }
//        if (solutions.isEmpty()) {
//            throw new IllegalStateException("You must provide at least one solution to the problem");
//        }
//        return new VersionCatalogProblem(
//            id,
//            severity,
//            context.get(),
//            shortDescription,
//            longDescription,
//            reason,
//            () -> docLink == null ? null : DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", docLink.getPage(), docLink.getSection()),
//            solutions.stream()
//                .map(DefaultCatalogProblemBuilder::toSolution)
//                .collect(toList())
//        );
//    }

//    private static Supplier<Solution> toSolution(Supplier<String> solutionText) {
//        return () -> new SimpleSolution(solutionText);
//    }

//    private static class SimpleSolution implements Solution {
//        private final Supplier<String> descriptor;
//
//        private SimpleSolution(Supplier<String> descriptor) {
//            this.descriptor = descriptor;
//        }
//
//        @Override
//        public String getShortDescription() {
//            return descriptor.get();
//        }
//    }
}
