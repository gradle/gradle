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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.changedetection.TaskArtifactStateCacheAccess;
import org.gradle.internal.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskPlanExecutorFactory implements Factory<TaskPlanExecutor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskPlanExecutorFactory.class);

    private final TaskArtifactStateCacheAccess taskArtifactStateCacheAccess;
    private final int parallelThreads;
    private final DocumentationRegistry documentationRegistry;

    public TaskPlanExecutorFactory(TaskArtifactStateCacheAccess taskArtifactStateCacheAccess, int parallelThreads, DocumentationRegistry documentationRegistry) {
        this.taskArtifactStateCacheAccess = taskArtifactStateCacheAccess;
        this.parallelThreads = parallelThreads;
        this.documentationRegistry = documentationRegistry;
    }

    public TaskPlanExecutor create() {
        ExecutionOptions options = new ExecutionOptions(parallelThreads);
        if (options.executeProjectsInParallel()) {
            String parallelWarningMessage = String.format(
                    "Parallel project execution is an \"incubating\" feature (%s). Many builds will not run correctly with this option.",
                    documentationRegistry.getFeatureLifecycle()
            );
            LOGGER.warn(parallelWarningMessage);
            return new ParallelTaskPlanExecutor(taskArtifactStateCacheAccess, options.numberOfParallelThreads());
        }
        return new DefaultTaskPlanExecutor();

    }
}
