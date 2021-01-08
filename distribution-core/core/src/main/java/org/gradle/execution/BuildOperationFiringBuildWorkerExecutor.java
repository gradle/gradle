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
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuildOperationFiringBuildWorkerExecutor implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationFiringBuildWorkerExecutor(BuildWorkExecutor delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void execute(GradleInternal gradle, Collection<? super Throwable> failures) {
        buildOperationExecutor.run(new ExecuteTasks(gradle, failures));
    }

    private class ExecuteTasks implements RunnableBuildOperation {
        private final GradleInternal gradle;
        private final Collection<? super Throwable> taskFailures;

        public ExecuteTasks(GradleInternal gradle, Collection<? super Throwable> taskFailures) {
            this.gradle = gradle;
            this.taskFailures = taskFailures;
        }

        @Override
        public void run(BuildOperationContext context) {
            List<Throwable> failures = new ArrayList<Throwable>();
            delegate.execute(gradle, failures);
            if (!failures.isEmpty()) {
                context.failed(failures.get(0));
            }
            taskFailures.addAll(failures);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Run tasks"));
            if (gradle.isRootBuild()) {
                builder.metadata(BuildOperationCategory.RUN_WORK_ROOT_BUILD);
                long buildStartTime = gradle.getServices().get(BuildRequestMetaData.class).getStartTime();
                builder.details(new RunRootBuildWorkBuildOperationType.Details(buildStartTime));
            } else {
                builder.metadata(BuildOperationCategory.RUN_WORK);
            }
            builder.totalProgress(gradle.getTaskGraph().size());
            return builder;
        }
    }
}
