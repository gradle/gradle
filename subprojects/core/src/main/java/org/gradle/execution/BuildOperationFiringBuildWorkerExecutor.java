/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;

public class BuildOperationFiringBuildWorkerExecutor implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationFiringBuildWorkerExecutor(BuildWorkExecutor delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle) {
        return buildOperationExecutor.call(new ExecuteTasks(gradle));
    }

    private class ExecuteTasks implements CallableBuildOperation<ExecutionResult<Void>> {
        private final GradleInternal gradle;

        public ExecuteTasks(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public ExecutionResult<Void> call(BuildOperationContext context) throws Exception {
            ExecutionResult<Void> result = delegate.execute(gradle);
            if (!result.getFailures().isEmpty()) {
                context.failed(result.getFailures().get(0));
            }
            return result;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Run tasks"));
            if (gradle.isRootBuild()) {
                long buildStartTime = gradle.getServices().get(BuildRequestMetaData.class).getStartTime();
                builder.details(new RunRootBuildWorkBuildOperationType.Details(buildStartTime));
            }
            builder.metadata(BuildOperationCategory.RUN_WORK);
            builder.totalProgress(gradle.getTaskGraph().size());
            return builder;
        }
    }
}
