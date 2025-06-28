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

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemReporterInternal;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.internal.execution.ExecutionProblemHandler;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.api.problems.Severity.WARNING;

public class DefaultExecutionProblemHandler implements ExecutionProblemHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutionProblemHandler.class);
    private final ValidateStep.ValidationWarningRecorder warningReporter;
    private final VirtualFileSystem virtualFileSystem;

    public DefaultExecutionProblemHandler(
        ValidateStep.ValidationWarningRecorder warningReporter,
        VirtualFileSystem virtualFileSystem
    ) {
        this.warningReporter = warningReporter;
        this.virtualFileSystem = virtualFileSystem;
    }

    @Override
    public void handleReportedProblems(UnitOfWork.Identity identity, UnitOfWork work, WorkValidationContext validationContext) {
        ProblemsInternal problemsService = validationContext.getProblemsService();
        ProblemReporterInternal reporter = problemsService.getInternalReporter();
        List<ProblemInternal> problems = validationContext.getProblems();

        Map<Severity, ImmutableList<ProblemInternal>> problemsMap = problems.stream()
            .collect(
                groupingBy(p -> p.getDefinition().getSeverity(),
                    mapping(identity(), toImmutableList())));
        List<ProblemInternal> warnings = problemsMap.getOrDefault(WARNING, ImmutableList.of());
        List<ProblemInternal> errors = problemsMap.getOrDefault(ERROR, ImmutableList.of());

        if (!warnings.isEmpty()) {
            for (ProblemInternal warning : warnings) {
                reporter.report(warning);
            }
            warningReporter.recordValidationWarnings(identity, work, warnings);
        }

        if (!errors.isEmpty()) {
            throwValidationException(work, validationContext, errors);
        }

        if (!warnings.isEmpty()) {
            LOGGER.info("Invalidating VFS because {} failed validation", work.getDisplayName());
            virtualFileSystem.invalidateAll();
        }
    }

    private static void throwValidationException(UnitOfWork work, WorkValidationContext validationContext, Collection<? extends ProblemInternal> validationErrors) {
        Set<String> uniqueErrors = validationErrors.stream()
            .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
            .collect(toImmutableSet());
        WorkValidationException workValidationException = WorkValidationException.forProblems(uniqueErrors)
            .withSummaryForContext(work.getDisplayName(), validationContext)
            .get();
        ProblemReporterInternal reporter = validationContext.getProblemsService().getInternalReporter();
        throw reporter.throwing(workValidationException, validationErrors);
    }
}
