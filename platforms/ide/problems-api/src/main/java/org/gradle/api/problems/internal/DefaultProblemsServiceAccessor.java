/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.ProblemsServiceAccessor;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class DefaultProblemsServiceAccessor implements ProblemsServiceAccessor {

    private final InternalProblems problemService;
    private String namespace = DefaultProblemCategory.getCoreNamespace();

    public DefaultProblemsServiceAccessor(InternalProblems problemService) {
        this.problemService = problemService;
    }

    @Override
    public ProblemsServiceAccessor withPluginNamespace(String pluginId) {
        namespace = DefaultProblemCategory.getPluginNamespace(pluginId);
        return this;
    }

    @Override
    public Problems get() {
        return new ProblemsServiceDelegate(this.problemService, namespace);
    }

    private static class ProblemsServiceDelegate implements InternalProblems {

        private final InternalProblems problemService;
        private final String namespace;

        private ProblemsServiceDelegate(InternalProblems problemService, String namespace) {
            this.problemService = problemService;
            this.namespace = namespace;
        }

        // TODO (donat) `Problems` method implementations were copied from `DefaultProblems`
        @Override
        public RuntimeException throwing(ProblemBuilderSpec action) {
            DefaultReportableProblemBuilder defaultProblemBuilder = createProblemBuilder();
            action.apply(defaultProblemBuilder);
            ReportableProblem problem = defaultProblemBuilder.build();
            throw throwError(problem.getException(), problem);
        }

        public RuntimeException throwError(RuntimeException exception, Problem problem) {
            report(problem);
            throw exception;
        }

        @Override
        public RuntimeException rethrowing(RuntimeException e, ProblemBuilderSpec action) {
            DefaultReportableProblemBuilder defaultProblemBuilder = createProblemBuilder();
            ProblemBuilder problemBuilder = action.apply(defaultProblemBuilder);
            problemBuilder.withException(e);
            throw throwError(e, defaultProblemBuilder.build());
        }

        @Override
        public ReportableProblem create(ProblemBuilderSpec action) {
            DefaultReportableProblemBuilder defaultProblemBuilder = createProblemBuilder();
            action.apply(defaultProblemBuilder);
            return defaultProblemBuilder.build();
        }

        @Override
        public DefaultReportableProblemBuilder createProblemBuilder() {
            return new DefaultReportableProblemBuilder(problemService, namespace);
        }

        @Override
        public void report(Problem problem) {
            problemService.report(problem);
        }
    }
}
