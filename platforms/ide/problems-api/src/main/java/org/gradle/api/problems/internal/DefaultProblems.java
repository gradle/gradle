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

import org.gradle.api.problems.ProblemReporter;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.jspecify.annotations.NonNull;

@ServiceScope(Scope.BuildTree.class)
public class DefaultProblems implements InternalProblems {

    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final ProblemSummarizer problemSummarizer;
    private final InternalProblemReporter internalReporter;
    private final ExceptionProblemRegistry exceptionProblemRegistry;
    private final ExceptionAnalyser exceptionAnalyser;
    private final ProblemsInfrastructure infrastructure;

    public DefaultProblems(
        ProblemSummarizer problemSummarizer,
        ProblemStream problemStream,
        CurrentBuildOperationRef currentBuildOperationRef,
        ExceptionProblemRegistry exceptionProblemRegistry,
        ExceptionAnalyser exceptionAnalyser,
        Instantiator instantiator,
        PayloadSerializer payloadSerializer,
        IsolatableFactory isolatableFactory,
        IsolatableToBytesSerializer isolatableSerializer
    ) {
        this.problemSummarizer = problemSummarizer;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.exceptionProblemRegistry = exceptionProblemRegistry;
        this.exceptionAnalyser = exceptionAnalyser;
        this.infrastructure = new ProblemsInfrastructure(new AdditionalDataBuilderFactory(), instantiator, payloadSerializer, isolatableFactory, isolatableSerializer, problemStream);
        this.internalReporter = createReporter();
    }

    @Override
    public ProblemReporter getReporter() {
        return createReporter();
    }

    @NonNull
    private DefaultProblemReporter createReporter() {
        return new DefaultProblemReporter(
            problemSummarizer,
            currentBuildOperationRef,
            exceptionProblemRegistry,
            exceptionAnalyser,
            infrastructure);
    }

    @Override
    public InternalProblemReporter getInternalReporter() {
        return internalReporter;
    }

    @Override
    public ProblemsInfrastructure getInfrastructure() {
        return infrastructure;
    }
    @Override
    public InternalProblemBuilder getProblemBuilder() {
        return new DefaultProblemBuilder(infrastructure);
    }
}
