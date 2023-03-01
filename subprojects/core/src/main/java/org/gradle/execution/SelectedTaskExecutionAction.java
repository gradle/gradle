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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.build.ExecutionResult;

import java.util.Set;

public class SelectedTaskExecutionAction implements BuildWorkExecutor {
    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
        bindAllReferencesOfProject(plan);
        return taskGraph.execute(plan);
    }

    private void bindAllReferencesOfProject(FinalizedExecutionPlan plan) {
        Set<Project> seen = Sets.newHashSet();
        plan.getContents().getScheduledNodes().visitNodes(nodes -> {
            for (Node node : nodes) {
                if (node instanceof LocalTaskNode) {
                    ProjectInternal taskProject = node.getOwningProject();
                    if (seen.add(taskProject)) {
                        taskProject.bindAllModelRules();
                    }
                }
            }
        });
    }
}
