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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.ConfigurationTimeBarrier;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.execution.TaskGraphBuildExecutionAction;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.ServiceRegistry;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultBuildWorkExecutor implements BuildWorkExecutor {

    private final ServiceRegistry buildScopeServices;
    private final PlanExecutor planExecutor;
    private final StyledTextOutputFactory textOutputFactory;
    private final ConfigurationTimeBarrier configurationTimeBarrier;
    private final BuildOperationRunner buildOperationRunner;

    public DefaultBuildWorkExecutor(
        ServiceRegistry buildScopeServices,
        PlanExecutor planExecutor,
        StyledTextOutputFactory textOutputFactory,
        ConfigurationTimeBarrier configurationTimeBarrier,
        BuildOperationRunner buildOperationRunner
    ) {
        this.buildScopeServices = buildScopeServices;
        this.planExecutor = planExecutor;
        this.textOutputFactory = textOutputFactory;
        this.configurationTimeBarrier = configurationTimeBarrier;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        return buildOperationRunner.call(new CallableBuildOperation<ExecutionResult<Void>>() {

            @Override
            public ExecutionResult<Void> call(BuildOperationContext context) {
                ExecutionResult<Void> result = displayOrExecutePlan(gradle, plan);
                if (!result.getFailures().isEmpty()) {
                    context.failed(result.getFailure());
                }
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Run tasks"));
                if (gradle.isRootBuild()) {
                    long buildStartTime = gradle.getServices().get(BuildRequestMetaData.class).getStartTime();
                    builder.details(new RunRootBuildWorkBuildOperationType.Details(buildStartTime));
                }
                builder.metadata(BuildOperationCategory.RUN_WORK);
                builder.totalProgress(plan.getContents().size());
                return builder;
            }

        });
    }

    public ExecutionResult<Void> displayOrExecutePlan(GradleInternal gradle, FinalizedExecutionPlan plan) {
        if (configurationTimeBarrier.isAtConfigurationTime()) {
            return executePlan(plan, gradle.getTaskGraph());
        }

        if (gradle.getStartParameter().isDryRun()) {
            for (Task task : plan.getContents().getTasks()) {
                textOutputFactory.create(DefaultBuildWorkExecutor.class)
                    .append(((TaskInternal) task).getIdentityPath().asString())
                    .append(" ")
                    .style(StyledTextOutput.Style.ProgressStatus)
                    .append("SKIPPED")
                    .println();
            }

            return ExecutionResult.succeeded();
        }

        if (gradle.getStartParameter().isTaskGraph()) {
            // The task sub-graph from an included build will be traversed and printed from the root build as well
            if (gradle.isRootBuild()) {
                TaskGraphBuildExecutionAction.renderTaskGraph(textOutputFactory, plan, gradle.getStartParameter());
            }

            return ExecutionResult.succeeded();
        }

        return executePlan(plan, gradle.getTaskGraph());
    }

    private ExecutionResult<Void> executePlan(FinalizedExecutionPlan plan, TaskExecutionGraphInternal taskGraph) {
        if (plan != taskGraph.getExecutionPlan()) {
            throw new IllegalStateException("Executed plan inconsistent with build's execution plan.");
        }

        bindAllReferencesOfProject(plan);

        taskGraph.getGraphExecutionListeners().beforeGraphExecutionStarts(plan.getContents());

        Map<ProjectInternal, NodeExecutionContext> projectContexts = new ConcurrentHashMap<>();
        NodeExecutionContext globalContext = new TaskGraphNodeExecutionContext(buildScopeServices);
        try {
            return planExecutor.process(
                plan.asWorkSource(),
                node -> {
                    // TODO: A Node should not expose its owning project.
                    // TODO: A Node should be created with the state required to execute it.
                    ProjectInternal project = node.getOwningProject();
                    if (project != null) {
                        NodeExecutionContext projectContext = projectContexts.computeIfAbsent(project, p ->
                            new TaskGraphNodeExecutionContext(ProjectExecutionServices.create(p))
                        );
                        node.execute(projectContext);
                    } else {
                        node.execute(globalContext);
                    }
                }
            );
        } finally {
            plan.close();
            taskGraph.depopulate();
            CompositeStoppable.stoppable(projectContexts.values()).stop();
        }
    }

    private static void bindAllReferencesOfProject(FinalizedExecutionPlan plan) {
        Set<Project> seen = new HashSet<>();
        for (Node node : plan.getContents().getScheduledNodes().getScheduledNodes()) {
            if (node instanceof LocalTaskNode) {
                ProjectInternal taskProject = node.getOwningProject();
                if (seen.add(taskProject)) {
                    taskProject.bindAllModelRules();
                }
            }
        }
    }

    private static class TaskGraphNodeExecutionContext implements NodeExecutionContext, Closeable {

        private final ServiceRegistry services;

        public TaskGraphNodeExecutionContext(ServiceRegistry services) {
            this.services = services;
        }

        @Override
        public <T> T getService(Class<T> type) throws ServiceLookupException {
            return services.get(type);
        }

        @Override
        public boolean isPartOfExecutionGraph() {
            return true;
        }

        @Override
        public void close() {
            if (services instanceof CloseableServiceRegistry) {
                ((CloseableServiceRegistry) services).close();
            }
        }

    }

}
