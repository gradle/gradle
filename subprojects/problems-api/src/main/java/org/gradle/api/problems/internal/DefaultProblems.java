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

import org.gradle.api.GradleException;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderSpec;
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.api.problems.locations.TaskPathLocation;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import java.util.List;
import java.util.Optional;

@ServiceScope(Scope.Global.class)
public class DefaultProblems implements InternalProblems {
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;
    private final List<ProblemTransformer> transformers;

    public DefaultProblems(
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        List<ProblemTransformer> transformers
    ) {
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
        this.transformers = transformers;
    }

    @Override
    public DefaultBuildableProblemBuilder createProblemBuilder() {
        return new DefaultBuildableProblemBuilder(this);
    }

    @Override
    public RuntimeException throwing(ProblemBuilderSpec action) {
        DefaultBuildableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.apply(defaultProblemBuilder);
        throw throwError(defaultProblemBuilder.getException(), defaultProblemBuilder.build());
    }

    @Override
    public RuntimeException rethrowing(RuntimeException e, ProblemBuilderSpec action) {
        DefaultBuildableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        ProblemBuilder problemBuilder = action.apply(defaultProblemBuilder);
        problemBuilder.withException(e);
        throw throwError(e, defaultProblemBuilder.build());
    }

    @Override
    public ReportableProblem createProblem(ProblemBuilderSpec action) {
        DefaultBuildableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.apply(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    public RuntimeException throwError(RuntimeException exception, Problem problem) {
        reportAsProgressEvent(problem);
        throw exception;
    }

    @Override
    public void reportAsProgressEvent(Problem problem) {
        // Transform the problem with all registered transformers
        for (ProblemTransformer transformer : transformers) {
            problem = transformer.transform(problem);
        }

        buildOperationProgressEventEmitter.emitNowIfCurrent(new DefaultProblemProgressDetails(problem));
    }
}
