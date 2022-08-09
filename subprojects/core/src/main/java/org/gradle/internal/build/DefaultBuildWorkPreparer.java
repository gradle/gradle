/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.ExecutionPlanFactory;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;

import java.util.function.Consumer;

public class DefaultBuildWorkPreparer implements BuildWorkPreparer {
    private final ExecutionPlanFactory executionPlanFactory;

    public DefaultBuildWorkPreparer(ExecutionPlanFactory executionPlanFactory) {
        this.executionPlanFactory = executionPlanFactory;
    }

    @Override
    public ExecutionPlan newExecutionPlan() {
        return executionPlanFactory.createPlan();
    }

    @Override
    public void populateWorkGraph(GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action) {
        action.accept(plan);
        plan.determineExecutionPlan();
    }

    @Override
    public void finalizeWorkGraph(GradleInternal gradle, ExecutionPlan plan) {
        TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
        plan.finalizePlan();
        taskGraph.populate(plan);
        BuildOutputCleanupRegistry buildOutputCleanupRegistry = gradle.getServices().get(BuildOutputCleanupRegistry.class);
        buildOutputCleanupRegistry.resolveOutputs();
    }
}
