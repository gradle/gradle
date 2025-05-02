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

package org.gradle.problems.internal.services;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.ExceptionProblemRegistry;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.internal.IsolatableToBytesSerializer;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.api.problems.internal.ProblemReportCreator;
import org.gradle.api.problems.internal.ProblemSummarizer;
import org.gradle.api.problems.internal.ProblemTaskIdentityTracker;
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.cc.impl.problems.BuildNameProvider;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.execution.WorkExecutionTracker;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.problems.internal.NoOpProblemReportCreator;
import org.gradle.problems.internal.emitters.BuildOperationBasedProblemEmitter;
import org.gradle.problems.internal.impl.DefaultProblemsReportCreator;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import java.util.Collection;

@ServiceScope(Scope.BuildTree.class)
public class ProblemsBuildTreeServices implements ServiceRegistrationProvider {
    @Provides
    InternalProblems createProblemsService(
        ProblemSummarizer problemSummarizer,
        ProblemStream problemStream,
        ExceptionProblemRegistry exceptionProblemRegistry,
        ExceptionAnalyser exceptionAnalyser,
        InstantiatorFactory instantiatorFactory,
        PayloadSerializer payloadSerializer,
        IsolatableFactory isolatableFactory,
        IsolatableToBytesSerializer isolatableToBytesSerializer,
        ServiceRegistry serviceRegistry
    ) {
        return new DefaultProblems(
            problemSummarizer,
            problemStream,
            CurrentBuildOperationRef.instance(),
            exceptionProblemRegistry,
            exceptionAnalyser,
            instantiatorFactory.decorateLenient(serviceRegistry),
            payloadSerializer,
            isolatableFactory,
            isolatableToBytesSerializer);
    }

    @Provides
    ProblemSummarizer createProblemSummarizer(
        BuildOperationProgressEventEmitter eventEmitter,
        CurrentBuildOperationRef currentBuildOperationRef,
        Collection<ProblemEmitter> problemEmitters,
        InternalOptions internalOptions,
        ProblemReportCreator problemReportCreator,
        WorkExecutionTracker workExecutionTracker
    ) {
        return new DefaultProblemSummarizer(eventEmitter,
            currentBuildOperationRef,
            ImmutableList.of(new BuildOperationBasedProblemEmitter(eventEmitter)),
            internalOptions,
            problemReportCreator,
            id -> {
                TaskIdentity taskIdentity = ProblemTaskIdentityTracker.getTaskIdentity();
                if (taskIdentity != null) {
                    return taskIdentity;
                } else {
                    return workExecutionTracker
                        .getCurrentTask(id)
                        .map(task -> new TaskIdentity(task.getTaskIdentity().getBuildPath(), task.getTaskIdentity().getTaskPath()))
                        .orElse(null);
                }
            }
        );
    }

    @Provides
    ProblemReportCreator createProblemsReportCreator(
        ExecutorFactory executorFactory,
        TemporaryFileProvider temporaryFileProvider,
        InternalOptions internalOptions,
        StartParameterInternal startParameter,
        ListenerManager listenerManager,
        FailureFactory failureFactory,
        BuildNameProvider buildNameProvider
    ) {
        if (startParameter.isProblemReportGenerationEnabled()) {
            return new DefaultProblemsReportCreator(executorFactory, temporaryFileProvider, internalOptions, startParameter, failureFactory, buildNameProvider);
        }
        return new NoOpProblemReportCreator();
    }
}
