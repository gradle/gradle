/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.util.Path;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

class TaskExecutionGraphTracker implements TaskExecutionGraphListener {

    private final Map<Path, TaskExecutionGraphInternal> taskExecutionGraphs = new HashMap<>();

    TaskExecutionGraphInternal getTaskExecutionGraph(Task task) {
        return getTaskExecutionGraph(((ProjectInternal) task.getProject()).getGradle().getIdentityPath());
    }

    TaskExecutionGraphInternal getTaskExecutionGraph(Path buildPath) {
        return taskExecutionGraphs.get(buildPath);
    }

    @Override
    public void graphPopulated(@Nonnull TaskExecutionGraph taskExecutionGraph) {
        Path buildPath = ((TaskExecutionGraphInternal) taskExecutionGraph).getRootProject().getGradle().getIdentityPath();
        taskExecutionGraphs.put(buildPath, (TaskExecutionGraphInternal) taskExecutionGraph);
    }

}
