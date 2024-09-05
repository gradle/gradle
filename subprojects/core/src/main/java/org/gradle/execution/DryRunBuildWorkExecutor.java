/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.operations.execution.CachingDisabledReasonCategory;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A {@link BuildWorkExecutor} that disables all selected tasks before they are executed.
 */
public class DryRunBuildWorkExecutor implements BuildWorkExecutor {
    private final BuildOperationRunner buildOperationRunner;
    private final BuildWorkExecutor delegate;

    public DryRunBuildWorkExecutor(BuildOperationRunner buildOperationRunner, BuildWorkExecutor delegate) {
        this.buildOperationRunner = buildOperationRunner;
        this.delegate = delegate;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        if (gradle.getStartParameter().isDryRun()) {
            // Using verbose to show the task headers
            gradle.getStartParameter().setConsoleOutput(ConsoleOutput.Verbose);
            for (Task task : plan.getContents().getTasks()) {
                TaskInternal taskInternal = (TaskInternal) task;
                // Emit the build operation, so the tasks show up in Build Scan as well
                buildOperationRunner.run(new RunnableBuildOperation() {
                    @Override
                    public void run(BuildOperationContext context) {
                        context.setStatus("SKIPPED");
                        context.setResult(new DryRunTaskResult(taskInternal.hasTaskActions()));
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        Path identityPath = taskInternal.getIdentityPath();
                        return BuildOperationDescriptor.displayName("Task " + identityPath)
                            .name(identityPath.toString())
                            .progressDisplayName(identityPath.toString())
                            .metadata(BuildOperationCategory.TASK)
                            .details(new DryRunTaskDetails(taskInternal.getTaskIdentity()));
                    }
                });
            }
            return ExecutionResult.succeeded();
        } else {
            return delegate.execute(gradle, plan);
        }
    }

    public static class DryRunTaskDetails implements ExecuteTaskBuildOperationType.Details {
        private final TaskIdentity<?> taskIdentity;

        public DryRunTaskDetails(TaskIdentity<?> taskIdentity) {
            this.taskIdentity = taskIdentity;
        }

        @Override
        public String getBuildPath() {
            return taskIdentity.getBuildPath();
        }

        @Override
        public String getTaskPath() {
            return taskIdentity.getTaskPath();
        }

        @Override
        public long getTaskId() {
            return taskIdentity.getId();
        }

        @Override
        public Class<?> getTaskClass() {
            return taskIdentity.getTaskType();
        }
    }

    private static class DryRunTaskResult implements ExecuteTaskBuildOperationType.Result {
        private final boolean hasTaskActions;

        public DryRunTaskResult(boolean hasTaskActions) {
            this.hasTaskActions = hasTaskActions;
        }

        @Nullable
        @Override
        public String getSkipMessage() {
            return TaskExecutionOutcome.SKIPPED.getMessage();
        }

        @Override
        public String getSkipReasonMessage() {
            return "Dry run";
        }

        @Override
        public boolean isActionable() {
            return hasTaskActions;
        }

        @Nullable
        @Override
        public String getOriginBuildInvocationId() {
            return null;
        }

        @Nullable
        @Override
        public byte[] getOriginBuildCacheKeyBytes() {
            return null;
        }

        @Nullable
        @Override
        public Long getOriginExecutionTime() {
            return null;
        }

        @Override
        public String getCachingDisabledReasonMessage() {
            return "Cacheability was not determined";
        }

        @Override
        public String getCachingDisabledReasonCategory() {
            return CachingDisabledReasonCategory.UNKNOWN.name();
        }

        @Nullable
        @Override
        public List<String> getUpToDateMessages() {
            return null;
        }

        @Override
        public boolean isIncremental() {
            return false;
        }
    }
}
