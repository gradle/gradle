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

package org.gradle.initialization;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import java.util.List;
import java.util.Set;

public class BuildOperatingFiringTaskExecutionPreparer implements TaskExecutionPreparer {
    private final TaskExecutionPreparer delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperatingFiringTaskExecutionPreparer(TaskExecutionPreparer delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void prepareForTaskExecution(GradleInternal gradle) {
        buildOperationExecutor.run(new CalculateTaskGraph(gradle));
    }

    private class CalculateTaskGraph implements RunnableBuildOperation {
        private final GradleInternal gradle;

        public CalculateTaskGraph(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public void run(BuildOperationContext buildOperationContext) {
            final TaskExecutionGraphInternal taskGraph = populateTaskGraph();

            buildOperationContext.setResult(new CalculateTaskGraphBuildOperationType.Result() {
                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(taskGraph.getRequestedTasks());
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(taskGraph.getFilteredTasks());
                }

                private List<String> toTaskPaths(Set<Task> tasks) {
                    return ImmutableSortedSet.copyOf(Collections2.transform(tasks, new Function<Task, String>() {
                        @Override
                        public String apply(Task task) {
                            return task.getPath();
                        }
                    })).asList();
                }
            });
        }

        TaskExecutionGraphInternal populateTaskGraph() {
            delegate.prepareForTaskExecution(gradle);
            return gradle.getTaskGraph();
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                .details(new CalculateTaskGraphBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().getPath();
                    }
                });
        }
    }
}
