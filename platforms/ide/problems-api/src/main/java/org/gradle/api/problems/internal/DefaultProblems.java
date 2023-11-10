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
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.buildtree.ProblemStream;

import java.util.Collections;
import java.util.List;

import static org.gradle.internal.problems.NoOpProblemDiagnosticsFactory.EMPTY_STREAM;

@ServiceScope(Scopes.BuildTree.class)
public class DefaultProblems implements InternalProblems {
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;
    private final List<ProblemTransformer> transformers;
    private final ProblemStream problemStream;

    public DefaultProblems(BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        this(buildOperationProgressEventEmitter, Collections.<ProblemTransformer>emptyList(), EMPTY_STREAM);
    }

    public DefaultProblems(
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        List<ProblemTransformer> transformers,
        ProblemStream problemStream
    ) {
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
        this.transformers = transformers;
        this.problemStream = problemStream;
    }

    @Override
    public DefaultReportableProblemBuilder createProblemBuilder() {
        return new DefaultReportableProblemBuilder(this);
    }

    @Override
    public ProblemStream getProblemStream() {
        return problemStream;
    }

    @Override
    public RuntimeException throwing(ProblemBuilderSpec action) {
        DefaultReportableProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.apply(defaultProblemBuilder);
        throw throwError(defaultProblemBuilder.getException(), defaultProblemBuilder.build());
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
