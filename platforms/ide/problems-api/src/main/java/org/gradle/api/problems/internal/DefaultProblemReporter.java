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

import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;

import java.util.List;

public class DefaultProblemReporter implements InternalProblemReporter {

    private final ProblemEmitter emitter;
    private final List<ProblemTransformer> transformers;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Multimap<Throwable, Problem> problems;

    public DefaultProblemReporter(
        ProblemEmitter emitter,
        List<ProblemTransformer> transformers,
        CurrentBuildOperationRef currentBuildOperationRef,
        Multimap<Throwable, Problem> problems
    ) {
        this.emitter = emitter;
        this.transformers = transformers;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.problems = problems;
    }

    @Override
    public void reporting(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = new DefaultProblemBuilder();
        spec.execute(problemBuilder);
        report(problemBuilder.build());
    }

    @Override
    public RuntimeException throwing(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = new DefaultProblemBuilder();
        spec.execute(problemBuilder);
        Problem problem = problemBuilder.build();
        RuntimeException exception = problem.getException();
        if (exception == null) {
            throw new IllegalStateException("Exception must be non-null");
        } else {
            throw throwError(exception, problem);
        }
    }

    private RuntimeException throwError(RuntimeException exception, Problem problem) {
        report(problem);
        problems.put(exception, problem);
        throw exception;
    }

    @Override
    public RuntimeException rethrowing(RuntimeException e, Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = new DefaultProblemBuilder();
        spec.execute(problemBuilder);
        problemBuilder.withException(e);
        throw throwError(e, problemBuilder.build());
    }

    @Override
    public Problem create(Action<InternalProblemSpec> action) {
        DefaultProblemBuilder defaultProblemBuilder = new DefaultProblemBuilder();
        action.execute(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    private Problem transformProblem(Problem problem, OperationIdentifier id) {
        for (ProblemTransformer transformer : transformers) {
            problem = transformer.transform(problem, id);
        }
        return problem;
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
        RuntimeException exception = problem.getException();
        if(exception != null) {
            problems.put(exception, problem);
        }
        OperationIdentifier id = currentBuildOperationRef.getId();
        if (id != null) {
            report(problem, id);
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
        emitter.emit(transformProblem(problem, id), id);
    }
}
