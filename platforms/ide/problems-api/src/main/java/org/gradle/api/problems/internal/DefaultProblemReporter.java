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
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

public class DefaultProblemReporter implements InternalProblemReporter {

    private final ProblemSummarizer problemSummarizer;
    private final ProblemsInfrastructure infrastructure;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final ExceptionProblemRegistry exceptionProblemRegistry;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultProblemReporter(
        ProblemSummarizer problemSummarizer,
        CurrentBuildOperationRef currentBuildOperationRef,
        ExceptionProblemRegistry exceptionProblemRegistry,
        ExceptionAnalyser exceptionAnalyser,
        ProblemsInfrastructure infrastructure
    ) {
        this.problemSummarizer = problemSummarizer;
        this.infrastructure = infrastructure;
        this.currentBuildOperationRef = currentBuildOperationRef;
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
    public RuntimeException throwing(Throwable exception, ProblemId problemId, Action<? super ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        problemBuilder.id(problemId);
        spec.execute(problemBuilder);
        problemBuilder.withException(exception);
        report(problemBuilder.build());
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
    private InternalProblem addExceptionToProblem(Throwable exception, Problem problem) {
        return getBuilder(problem).withException(transform(exception)).build();
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
    public InternalProblem internalCreate(Action<? super InternalProblemSpec> action) {
        DefaultProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.execute(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    /**
     * Reports a problem.
     * <p>
     * The current build operation is used as the operation identifier.
     * If there is no current build operation, the problem is not reported.
     *
     * @param problem The problem to report.
     */
    @Override
    public void report(Problem problem) {
        OperationIdentifier id = currentBuildOperationRef.getId();
        if (id != null) {
            report(problem, id);
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
        InternalProblem internalProblem = (InternalProblem) problem;
        Throwable exception = internalProblem.getException();
        if (exception != null) {
            exceptionProblemRegistry.onProblem(transform(exception), internalProblem);
        }
        problemSummarizer.emit(internalProblem, id);
    }

    @NonNull
    private InternalProblemBuilder getBuilder(Problem problem) {
        return ((InternalProblem) problem).toBuilder(infrastructure);
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
