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

import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess;
import org.gradle.initialization.layout.GradleProperties;
import org.gradle.internal.Factory;
import org.gradle.util.SingleMessageLogger;

public class TaskPlanExecutorFactory implements Factory<TaskPlanExecutor> {

    private final TaskArtifactStateCacheAccess taskArtifactStateCacheAccess;
    private final int parallelThreads;

    public TaskPlanExecutorFactory(TaskArtifactStateCacheAccess taskArtifactStateCacheAccess, int parallelThreads) {
        this.taskArtifactStateCacheAccess = taskArtifactStateCacheAccess;
        this.parallelThreads = parallelThreads;
    }

    public TaskPlanExecutor create() {
        ExecutionOptions options = new ExecutionOptions(parallelThreads);
        if (options.executeProjectsInParallel()) {
            SingleMessageLogger.incubatingModeUsed("Parallel project execution", GradleProperties.PARALLEL_PROPERTY);
            return new ParallelTaskPlanExecutor(taskArtifactStateCacheAccess, options.numberOfParallelThreads());
        }
        return new DefaultTaskPlanExecutor(taskArtifactStateCacheAccess);
    }
}
