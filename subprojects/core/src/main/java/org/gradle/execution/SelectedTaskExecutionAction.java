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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.NodeExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.service.ServiceRegistry;


public class SelectedTaskExecutionAction implements BuildWorkExecutor {

    private final ServiceRegistry buildScopeServices;
    private final PlanExecutor planExecutor;
    private final BuildOperationRunner buildOperationRunner;
    private final NodeExecutor nodeExecutor;

    public SelectedTaskExecutionAction(
        ServiceRegistry buildScopeServices,
        PlanExecutor planExecutor,
        BuildOperationRunner buildOperationRunner,
        NodeExecutor nodeExecutor
    ) {
        this.buildScopeServices = buildScopeServices;
        this.planExecutor = planExecutor;
        this.buildOperationRunner = buildOperationRunner;
        this.nodeExecutor = nodeExecutor;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
        if (plan != taskGraph.getExecutionPlan()) {
            throw new IllegalStateException("Executed plan inconsistent with build's execution plan.");
        }
        taskGraph.getGraphExecutionListeners().beforeGraphExecutionStarts(plan.getContents());
        BuildOperationRef parentOperation = buildOperationRunner.getCurrentOperation();
        try (ProjectExecutionServiceRegistry projectExecutionServices = new ProjectExecutionServiceRegistry(buildScopeServices)) {
            return planExecutor.process(
                plan.asWorkSource(),
                node -> {
                    CurrentBuildOperationRef.instance().with(parentOperation, () -> {
                        NodeExecutionContext context = projectExecutionServices.forProject(node.getOwningProject());
                        if (nodeExecutor.execute(node, context)) {
                            return;
                        }
                        throw new IllegalStateException("Unknown type of node: " + node);
                    });
                }
            );
        } finally {
            plan.close();
            taskGraph.depopulate();
        }
    }

}
