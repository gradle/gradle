/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType;

public class BuildOperationFiringBuildTreeWorkExecutor implements BuildTreeWorkExecutor {
    private static final RunRequestedWorkBuildOperationType.Details DETAILS = new RunRequestedWorkBuildOperationType.Details() {};
    private final BuildTreeWorkExecutor delegate;
    private final BuildOperationRunner buildOperationRunner;

    public BuildOperationFiringBuildTreeWorkExecutor(BuildTreeWorkExecutor delegate, BuildOperationRunner buildOperationRunner) {
        this.delegate = delegate;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public ExecutionResult<Void> execute(BuildTreeWorkGraph.FinalizedGraph graph) {
        return buildOperationRunner.call(new CallableBuildOperation<ExecutionResult<Void>>() {
            @Override
            public ExecutionResult<Void> call(BuildOperationContext context) {
                return delegate.execute(graph);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName("Run main tasks");
                builder.details(DETAILS);
                builder.metadata(BuildOperationCategory.RUN_MAIN_TASKS);
                return builder;
            }
        });
    }
}
