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

import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.configuration.problems.ProblemFactory;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.problems.internal.DefaultProblemsReportCreator;
import org.gradle.problems.internal.emitters.BuildOperationBasedProblemEmitter;

import java.util.Collection;

@ServiceScope(Scope.BuildTree.class)
public class ProblemsBuildTreeServices implements ServiceRegistrationProvider {

    @Provides
    InternalProblems createProblemsService(
        ProblemStream problemStream,
        Collection<ProblemEmitter> problemEmitters
    ) {
        return new DefaultProblems(problemEmitters, problemStream, CurrentBuildOperationRef.instance());
    }

    @Provides
    ProblemEmitter createProblemEmitter(BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        return new BuildOperationBasedProblemEmitter(buildOperationProgressEventEmitter);
    }

    @Provides
    DefaultProblemsReportCreator createProblemsReportCreator(
        ExecutorFactory executorFactory,
        TemporaryFileProvider temporaryFileProvider,
        InternalOptions internalOptions,
        ProblemFactory problemFactory
    ) {
        return new DefaultProblemsReportCreator(executorFactory, temporaryFileProvider, internalOptions, problemFactory);
    }
}
