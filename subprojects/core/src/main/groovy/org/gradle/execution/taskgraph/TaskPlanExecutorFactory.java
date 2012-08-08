/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.internal.changedetection.TaskArtifactStateCacheAccess;
import org.gradle.internal.Factory;

public class TaskPlanExecutorFactory implements Factory<TaskPlanExecutor> {
    private final TaskArtifactStateCacheAccess taskArtifactStateCacheAccess;
    private final int parallelExecutors;

    public TaskPlanExecutorFactory(TaskArtifactStateCacheAccess taskArtifactStateCacheAccess, int parallelExecutors) {
        this.taskArtifactStateCacheAccess = taskArtifactStateCacheAccess;
        this.parallelExecutors = parallelExecutors;
    }

    public TaskPlanExecutor create() {
        ExecutionOptions options = new ExecutionOptions(parallelExecutors);
        if (options.executeProjectsInParallel()) {
            return new ParallelTaskPlanExecutor(taskArtifactStateCacheAccess, options.numberOfParallelExecutors());
        }
        return new DefaultTaskPlanExecutor();

    }
}
