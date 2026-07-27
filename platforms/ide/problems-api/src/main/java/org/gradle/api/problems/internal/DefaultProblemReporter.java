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

import org.gradle.api.Action;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.operations.BuildOperationIdRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class DefaultProblemReporter implements ProblemReporterInternal {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProblemReporter.class);

    private final ProblemSummarizer problemSummarizer;
    private final ProblemsInfrastructure infrastructure;
    private final BuildOperationIdRef operationIdRef;
    private final ExceptionProblemRegistry exceptionProblemRegistry;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultProblemReporter(
        ProblemSummarizer problemSummarizer,
        BuildOperationIdRef operationIdRef,
        ExceptionProblemRegistry exceptionProblemRegistry,
        ExceptionAnalyser exceptionAnalyser,
        ProblemsInfrastructure infrastructure
    ) {
        this.problemSummarizer = problemSummarizer;
        this.infrastructure = infrastructure;
        this.operationIdRef = operationIdRef;
        this.exceptionProblemRegistry = exceptionProblemRegistry;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    @Override
    public void report(ProblemId problemId, Action<? super ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        problemBuilder.id(problemId);
        spec.execute(problemBuilder);
        report(problemBuilder.build());
    }

    @NonNull
    private DefaultProblemBuilder createProblemBuilder() {
        return new DefaultProblemBuilder(infrastructure);
    }

    @Override
    public void reportError(Problem problem) {
        problem = getBuilder(problem).internalSeverity(Severity.ERROR).build();
        report(problem);
    }

    @Override
    public void reportError(Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            reportError(problem);
        }
    }

    @Override
    public RuntimeException throwing(Throwable exception, ProblemId problemId, Action<? super ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        problemBuilder.id(problemId);
        spec.execute(problemBuilder);
        problemBuilder.withException(exception);
        report(addExceptionToProblem(exception, problemBuilder.build()));
        throw runtimeException(exception);
    }

    @Override
    public RuntimeException throwing(Throwable exception, Problem problem) {
        problem = addExceptionToProblem(exception, problem);
        report(problem);
        throw runtimeException(exception);
    }

    @Override
    public RuntimeException throwing(Throwable exception, Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            report(addExceptionToProblem(exception, problem));
        }
        throw runtimeException(exception);
    }

    @NonNull
    private ProblemInternal addExceptionToProblem(Throwable exception, Problem problem) {
        return getBuilder(problem).internalSeverity(Severity.ERROR).withException(transform(exception)).build();
    }

    private static RuntimeException runtimeException(Throwable exception) {
        if (exception instanceof RuntimeException) {
            return (RuntimeException) exception;
        } else {
            return new RuntimeException(exception);
        }
    }

    @Override
    public Problem create(ProblemId problemId, Action<? super ProblemSpec> action) {
        DefaultProblemBuilder defaultProblemBuilder = createProblemBuilder();
        defaultProblemBuilder.id(problemId);
        action.execute(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    @Override
    public ProblemInternal internalCreate(Action<? super ProblemSpecInternal> action) {
        DefaultProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.execute(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    /**
     * Reports a problem.
     * <p>
     * The problem is attributed to the build operation provided by this reporter's
     * {@link BuildOperationIdRef} — typically the current build operation of the reporting
     * thread, falling back to the root build operation for threads that have no current
     * operation (for example, a thread dispatching build events to user-provided listeners).
     * Only when no operation is available at all is the problem discarded.
     *
     * @param problem The problem to report.
     */
    @Override
    public void report(Problem problem) {
        OperationIdentifier id = operationIdRef.getId();
        if (id != null) {
            report(problem, id);
        } else {
            ProblemId problemId = ((ProblemInternal) problem).getDefinition().getId();
            LOGGER.info("Discarding problem '{}:{}': no build operation is available to attribute it to on this thread", problemId.getGroup().getName(), problemId.getName());
        }
    }

    @Override
    public void report(Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            report(problem);
        }
    }

    /**
     * Reports a problem with an explicit operation identifier.
     * <p>
     * The operation identifier should not be null,
     * otherwise the behavior will be defined by the used {@link ProblemEmitter}.
     *
     * @param problem The problem to report.
     * @param id The operation identifier to associate with the problem.
     */
    @Override
    public void report(Problem problem, OperationIdentifier id) {
        ProblemInternal problemInternal = (ProblemInternal) problem;
        Throwable exception = problemInternal.getException();
        if (exception != null) {
            exceptionProblemRegistry.onProblem(transform(exception), problemInternal);
        }
        problemSummarizer.emit(problemInternal, id);
    }

    @NonNull
    private ProblemBuilderInternal getBuilder(Problem problem) {
        return ((ProblemInternal) problem).toBuilder(infrastructure);
    }

    private Throwable transform(Throwable failure) {
        if (exceptionAnalyser == null) {
            return failure;
        }
        try {
            return exceptionAnalyser.transform(failure).getCause();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
