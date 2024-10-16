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
import org.gradle.problems.buildtree.ProblemStream;

import java.util.Collection;

public class DefaultProblemReporter implements InternalProblemReporter {

    private final Collection<ProblemEmitter> emitters;
    private final ProblemStream problemStream;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Multimap<Throwable, Problem> problems;
    private final AdditionalDataBuilderFactory additionalDataBuilderFactory;

    public DefaultProblemReporter(
        Collection<ProblemEmitter> emitters,
        ProblemStream problemStream,
        CurrentBuildOperationRef currentBuildOperationRef,
        Multimap<Throwable, Problem> problems,
        AdditionalDataBuilderFactory additionalDataBuilderFactory
    ) {
        this.emitters = emitters;
        this.problemStream = problemStream;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.problems = problems;
        this.additionalDataBuilderFactory = additionalDataBuilderFactory;
    }

    @Override
    public void reporting(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = new DefaultProblemBuilder(problemStream, additionalDataBuilderFactory);
        spec.execute(problemBuilder);
        report(problemBuilder.build());
    }

    @Override
    public RuntimeException throwing(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = new DefaultProblemBuilder(problemStream, additionalDataBuilderFactory);
        spec.execute(problemBuilder);
        Problem problem = problemBuilder.build();
        Throwable exception = problem.getException();
        if (exception == null) {
            String message = problem.getContextualLabel() != null ? problem.getContextualLabel() : problem.getDefinition().getId().getDisplayName();
            exception = new RuntimeException(message);
        }
        return throwError(exception, problem);
    }

    private RuntimeException throwError(Throwable exception, Problem problem) {
        report(problem);
        problems.put(exception, problem);
        if (exception instanceof RuntimeException) {
            return (RuntimeException) exception;
        } else {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public Problem create(Action<InternalProblemSpec> action) {
        DefaultProblemBuilder defaultProblemBuilder = new DefaultProblemBuilder(problemStream, additionalDataBuilderFactory);
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
        Throwable exception = problem.getException();
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
        // TODO (reinhold) Reconsider using the Emitter interface here. Maybe it should be replaced with a future problem listener feature.
        for (ProblemEmitter emitter : emitters) {
            emitter.emit(problem, id);
        }
    }
}
