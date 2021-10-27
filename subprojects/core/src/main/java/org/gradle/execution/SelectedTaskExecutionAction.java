/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;

import java.util.Collection;
import java.util.Set;

public class SelectedTaskExecutionAction implements BuildExecutionAction {
    @Override
    public void execute(BuildExecutionContext context, Collection<? super Throwable> taskFailures) {
        GradleInternal gradle = context.getGradle();
        TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
        if (gradle.getStartParameter().isContinueOnFailure()) {
            taskGraph.setContinueOnFailure(true);
        }

        bindAllReferencesOfProject(taskGraph);
        taskGraph.execute(taskFailures);
    }

    private void bindAllReferencesOfProject(TaskExecutionGraph graph) {
        Set<Project> seen = Sets.newHashSet();
        for (Task task : graph.getAllTasks()) {
            if (seen.add(task.getProject())) {
                ProjectInternal projectInternal = (ProjectInternal) task.getProject();
                projectInternal.bindAllModelRules();
            }
        }
    }
}
