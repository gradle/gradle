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

import org.gradle.api.problems.ProblemTransformer;
import org.gradle.api.problems.ProblemsServiceAccessor;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.DefaultProblemsServiceAccessor;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.internal.OperationListener;
import org.gradle.problems.internal.emitters.BuildOperationBasedProblemEmitter;
import org.gradle.problems.internal.transformers.PluginIdLocationTransformer;
import org.gradle.problems.internal.transformers.StackLocationTransformer;

import java.util.List;

public class ProblemsBuildTreeServices {

    InternalProblems createProblemsService(
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        List<ProblemTransformer> transformers
    ) {
        BuildOperationBasedProblemEmitter emitter = new BuildOperationBasedProblemEmitter(buildOperationProgressEventEmitter);
        return new DefaultProblems(emitter, transformers);
    }

    ProblemsServiceAccessor createProblemsServiceAccessor(InternalProblems problems) {
        return new DefaultProblemsServiceAccessor(problems);
    }

    ProblemTransformer createPluginIdLocationTransformer(BuildOperationAncestryTracker buildOperationAncestryTracker, OperationListener operationListener) {
        return new PluginIdLocationTransformer(buildOperationAncestryTracker, operationListener);
    }

    ProblemTransformer createStackLocationTransformer(ProblemDiagnosticsFactory problemDiagnosticsFactory) {
        return new StackLocationTransformer(problemDiagnosticsFactory.newStream());
    }
}
