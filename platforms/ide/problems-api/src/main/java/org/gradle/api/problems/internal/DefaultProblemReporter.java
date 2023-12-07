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
import org.gradle.api.problems.ProblemSpec;

import java.util.List;

public class DefaultProblemReporter implements InternalProblemReporter {

    private final ProblemEmitter emitter;
    private final List<ProblemTransformer> transformers;
    private final String namespace;

    public DefaultProblemReporter(ProblemEmitter emitter, List<ProblemTransformer> transformers, String namespace) {
        this.emitter = emitter;
        this.transformers = transformers;
        this.namespace = namespace;
    }

    @Override
    public void reporting(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        spec.execute(problemBuilder);
        report(problemBuilder.build());
    }

    @Override
    public RuntimeException throwing(Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        spec.execute(problemBuilder);
        Problem problem = problemBuilder.build();
        RuntimeException exception = problem.getException();
        if (exception == null) {
            throw new IllegalStateException("Exception must be non-null");
        } else {
            throw throwError(exception, problem);
        }
    }

    public RuntimeException throwError(RuntimeException exception, Problem problem) {
        report(problem);
        throw exception;
    }

    @Override
    public RuntimeException rethrowing(RuntimeException e, Action<ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        spec.execute(problemBuilder);
        problemBuilder.withException(e);
        throw throwError(e, problemBuilder.build());
    }

    @Override
    public Problem create(Action<ProblemSpec> action) {
        DefaultProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.execute(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    // This method is only public to integrate with the existing task validation framework.
    // We should rework this integration and this method private.
    public DefaultProblemBuilder createProblemBuilder() {
        return new DefaultProblemBuilder(namespace);
    }

    @Override
    public void report(Problem problem) {
        // Transform the problem with all registered transformers
        for (ProblemTransformer transformer : transformers) {
            problem = transformer.transform((InternalProblem) problem);
        }

        emitter.emit(problem);
    }
}
