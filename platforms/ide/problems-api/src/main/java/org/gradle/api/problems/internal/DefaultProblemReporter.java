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
import org.gradle.api.problems.ProblemSpec;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.problems.buildtree.ProblemStream;

import javax.annotation.Nonnull;
import java.util.Collection;

public class DefaultProblemReporter implements InternalProblemReporter {

    private final ProblemSummarizer problemSummarizer;
    private final ProblemStream problemStream;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final ExceptionProblemRegistry exceptionProblemRegistry;
    private final AdditionalDataBuilderFactory additionalDataBuilderFactory;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultProblemReporter(
        ProblemSummarizer problemSummarizer,
        ProblemStream problemStream,
        CurrentBuildOperationRef currentBuildOperationRef,
        AdditionalDataBuilderFactory additionalDataBuilderFactory,
        ExceptionProblemRegistry exceptionProblemRegistry,
        ExceptionAnalyser exceptionAnalyser
    ) {
        this.problemSummarizer = problemSummarizer;
        this.problemStream = problemStream;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.exceptionProblemRegistry = exceptionProblemRegistry;
        this.additionalDataBuilderFactory = additionalDataBuilderFactory;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    @Override
    public void reporting(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        spec.execute(problemBuilder);
        report(problemBuilder.build());
    }

    @Nonnull
    private DefaultProblemBuilder createProblemBuilder() {
        return new DefaultProblemBuilder(problemStream, additionalDataBuilderFactory);
    }

    @Override
    public RuntimeException throwing(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        spec.execute(problemBuilder);

        Problem problem = problemBuilder.build();
        Throwable exception = problem.getException();
        if (exception == null) {
            throw new IllegalStateException("Exception must be non-null");
        } else {
            throw throwError(exception, problem);
        }
    }

    @Override
    public RuntimeException throwing(Throwable exception, Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            Problem problemWithException = new DefaultProblem(
                problem.getDefinition(),
                problem.getContextualLabel(),
                problem.getSolutions(),
                problem.getOriginLocations(),
                problem.getContextualLocations(),
                problem.getDetails(),
                transform(exception),
                problem.getAdditionalData()
            );
            report(problemWithException);
        }
        if (exception instanceof RuntimeException) {
            return (RuntimeException) exception;
        } else {
            throw new RuntimeException(exception);
        }
    }

    private RuntimeException throwError(Throwable exception, Problem problem) {
        report(problem);
        if (exception instanceof RuntimeException) {
            return (RuntimeException) exception;
        } else {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public Problem create(Action<InternalProblemSpec> action) {
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
        Throwable exception = problem.getException();
        if (exception != null) {
            exceptionProblemRegistry.onProblem(transform(exception), problem);
        }
        problemSummarizer.emit(problem, id);
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
