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

package org.gradle.internal.execution.impl;

import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.internal.execution.ExecutionProblemHandler;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

public class DefaultExecutionProblemHandler implements ExecutionProblemHandler {

    @Override
    public void handleReportedProblems(Identity identity, UnitOfWork work, WorkValidationContext validationContext) {
        List<InternalProblem> problems = validationContext.getProblems();
        if (!problems.isEmpty()) {
            throwValidationException(work, validationContext, problems);
        }
    }

    private static void throwValidationException(UnitOfWork work, WorkValidationContext validationContext, Collection<? extends InternalProblem> validationErrors) {
        Set<String> uniqueErrors = validationErrors.stream()
            .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
            .collect(toImmutableSet());
        WorkValidationException workValidationException = WorkValidationException.forProblems(uniqueErrors)
            .withSummaryForContext(work.getDisplayName(), validationContext)
            .get();
        ProblemReporter reporter = validationContext.getProblemsService().getReporter();
        throw reporter.throwing(workValidationException, validationErrors);
    }
}
