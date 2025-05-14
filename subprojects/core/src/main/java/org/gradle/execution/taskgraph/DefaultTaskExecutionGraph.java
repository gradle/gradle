/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.BuildScopeListenerRegistrationListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.tasks.TaskState;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.execution.ProjectExecutionServiceRegistry;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.NodeExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.plan.TaskNode;
import org.gradle.internal.Cast;
import org.gradle.internal.InternalListener;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@SuppressWarnings("deprecation")
@NullMarked
public class DefaultTaskExecutionGraph implements TaskExecutionGraphInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskExecutionGraph.class);

    private final PlanExecutor planExecutor;
    private final List<NodeExecutor> nodeExecutors;
    private final GradleInternal gradleInternal;
    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> taskListeners;
    private final BuildScopeListenerRegistrationListener buildScopeListenerRegistrationListener;
    private final ServiceRegistry globalServices;
    private final BuildOperationRunner buildOperationRunner;
    private final ListenerBuildOperationDecorator listenerBuildOperationDecorator;
    private FinalizedExecutionPlan executionPlan;
    private List<Task> allTasks = Collections.emptyList();
    private boolean hasFiredWhenReady;

    public DefaultTaskExecutionGraph(
        PlanExecutor planExecutor,
        List<NodeExecutor> nodeExecutors,
        BuildOperationRunner buildOperationRunner,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator,
        GradleInternal gradleInternal,
        ListenerBroadcast<TaskExecutionGraphListener> graphListeners,
        ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> taskListeners,
        BuildScopeListenerRegistrationListener buildScopeListenerRegistrationListener,
        ServiceRegistry globalServices
    ) {
        this.planExecutor = planExecutor;
        this.nodeExecutors = nodeExecutors;
        this.buildOperationRunner = buildOperationRunner;
        this.listenerBuildOperationDecorator = listenerBuildOperationDecorator;
        this.gradleInternal = gradleInternal;
        this.graphListeners = graphListeners;
        this.taskListeners = taskListeners;
        this.buildScopeListenerRegistrationListener = buildScopeListenerRegistrationListener;
        this.globalServices = globalServices;
        this.executionPlan = FinalizedExecutionPlan.EMPTY;
    }

    @Override
    public void populate(FinalizedExecutionPlan plan) {
        executionPlan.close();
        executionPlan = plan;
        // Take a snapshot of all tasks, as nodes are removed from the plan as they execute
        allTasks = ImmutableList.copyOf(executionPlan.getContents().getTasks());
        if (!hasFiredWhenReady) {
            fireWhenReady();
            hasFiredWhenReady = true;
        } else if (!graphListeners.isEmpty()) {
            LOGGER.info("Ignoring listeners of task graph ready event, as this build ({}) has already executed work.", gradleInternal.getIdentityPath());
        }
    }

    @Override
    public ExecutionResult<Void> execute(FinalizedExecutionPlan plan) {
        assertIsThisGraphsPlan(plan);
        if (!hasFiredWhenReady) {
            throw new IllegalStateException("Task graph should be populated before execution starts.");
        }
        try (ProjectExecutionServiceRegistry projectExecutionServices = new ProjectExecutionServiceRegistry(globalServices)) {
            return executeWithServices(projectExecutionServices);
        } finally {
            executionPlan.close();
            executionPlan = FinalizedExecutionPlan.EMPTY;
        }
    }

    private void assertIsThisGraphsPlan(FinalizedExecutionPlan plan) {
        if (plan != executionPlan) {
            // Temporarily handle only a single plan
            throw new IllegalArgumentException();
        }
    }

    private ExecutionResult<Void> executeWithServices(ProjectExecutionServiceRegistry projectExecutionServices) {
        return planExecutor.process(
            executionPlan.asWorkSource(),
            new BuildOperationAwareExecutionAction(
                buildOperationRunner.getCurrentOperation(),
                new InvokeNodeExecutorsAction(nodeExecutors, projectExecutionServices)
            )
        );
    }

    @Override
    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(
            decorateListener("TaskExecutionGraph.addTaskExecutionGraphListener", listener)
        );
    }

    private TaskExecutionGraphListener decorateListener(String registrationPoint, TaskExecutionGraphListener listener) {
        return listenerBuildOperationDecorator.decorate(
            registrationPoint,
            TaskExecutionGraphListener.class,
            listener
        );
    }

    @Override
    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    @Override
    public void whenReady(final Closure closure) {
        graphListeners.add(
            new ClosureBackedMethodInvocationDispatch(
                "graphPopulated",
                listenerBuildOperationDecorator.decorate(
                    "TaskExecutionGraph.whenReady",
                    Cast.<Closure<?>>uncheckedCast(closure)
                )
            )
        );
    }

    @Override
    public void whenReady(final Action<TaskExecutionGraph> action) {
        graphListeners.add(
            decorateListener("TaskExecutionGraph.whenReady", action::execute)
        );
    }

    @Override
    public void addTaskExecutionListener(org.gradle.api.execution.TaskExecutionListener listener) {
        notifyListenerRegistration("TaskExecutionGraph.addTaskExecutionListener", listener);
        taskListeners.add(listener);
    }

    @Override
    public void removeTaskExecutionListener(org.gradle.api.execution.TaskExecutionListener listener) {
        taskListeners.remove(listener);
    }

    @Override
    public void beforeTask(final Closure closure) {
        notifyListenerRegistration("TaskExecutionGraph.beforeTask", closure);
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("beforeExecute", closure));
    }

    @Override
    public void beforeTask(final Action<Task> action) {
        notifyListenerRegistration("TaskExecutionGraph.beforeTask", action);
        taskListeners.add(new TaskExecutionAdapter() {
            @Override
            public void beforeExecute(Task task) {
                action.execute(task);
            }
        });
    }

    @Override
    public void afterTask(final Closure closure) {
        notifyListenerRegistration("TaskExecutionGraph.afterTask", closure);
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("afterExecute", closure));
    }

    @Override
    public void afterTask(final Action<Task> action) {
        notifyListenerRegistration("TaskExecutionGraph.afterTask", action);
        taskListeners.add(new TaskExecutionAdapter() {
            @Override
            public void afterExecute(Task task, TaskState state) {
                action.execute(task);
            }
        });
    }

    private void notifyListenerRegistration(String registrationPoint, Object listener) {
        if (listener instanceof InternalListener) {
            return;
        }
        buildScopeListenerRegistrationListener.onBuildScopeListenerRegistration(
            listener,
            registrationPoint,
            this
        );
    }

    @Override
    public boolean hasTask(Task task) {
        return executionPlan.getContents().getTasks().contains(task);
    }

    @Nullable
    @Override
    public Task findTask(String path) {
        for (Task task : executionPlan.getContents().getTasks()) {
            if (task.getPath().equals(path)) {
                return task;
            }
        }
        return null;
    }

    @Override
    public boolean hasTask(String path) {
        return findTask(path) != null;
    }

    @Override
    public int size() {
        return executionPlan.getContents().size();
    }

    @Override
    public List<Task> getAllTasks() {
        return allTasks;
    }

    @Override
    public void visitScheduledNodes(BiConsumer<List<Node>, Set<Node>> visitor) {
        executionPlan.getContents().getScheduledNodes().visitNodes(visitor);
    }

    @Override
    public Set<Task> getDependencies(Task task) {
        Node node = executionPlan.getContents().getNode(task);
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (Node dependencyNode : node.getDependencySuccessors()) {
            if (dependencyNode instanceof TaskNode) {
                builder.add(((TaskNode) dependencyNode).getTask());
            }
        }
        return builder.build();
    }

    @Override
    public void resetState() {
        graphListeners.removeAll();
        taskListeners.removeAll();
        executionPlan.close();
        executionPlan = FinalizedExecutionPlan.EMPTY;
        allTasks = Collections.emptyList();
    }

    /**
     * This action wraps the execution of a node into a build operation.
     */
    private static class BuildOperationAwareExecutionAction implements Action<Node> {
        private final BuildOperationRef parentOperation;
        private final Action<Node> delegate;

        BuildOperationAwareExecutionAction(BuildOperationRef parentOperation, Action<Node> delegate) {
            this.parentOperation = parentOperation;
            this.delegate = delegate;
        }

        @Override
        public void execute(Node node) {
            CurrentBuildOperationRef.instance().with(parentOperation, () -> delegate.execute(node));
        }
    }

    private static class InvokeNodeExecutorsAction implements Action<Node> {
        private final List<NodeExecutor> nodeExecutors;
        private final ProjectExecutionServiceRegistry projectExecutionServices;

        public InvokeNodeExecutorsAction(List<NodeExecutor> nodeExecutors, ProjectExecutionServiceRegistry projectExecutionServices) {
            this.nodeExecutors = nodeExecutors;
            this.projectExecutionServices = projectExecutionServices;
        }

        @Override
        public void execute(Node node) {
            NodeExecutionContext context = projectExecutionServices.forProject(node.getOwningProject());
            for (NodeExecutor nodeExecutor : nodeExecutors) {
                if (nodeExecutor.execute(node, context)) {
                    return;
                }
            }
            throw new IllegalStateException("Unknown type of node: " + node);
        }
    }

    @Override
    public Set<Task> getFilteredTasks() {
        /*
            Note: we currently extract this information from the execution plan because it's
            buried under functions in #filter. This could be detangled/simplified by introducing
            excludeTasks(Iterable<Task>) as an analog to addEntryTasks(Iterable<Task>).

            This is too drastic a change for the stage in the release cycle were exposing this information
            was necessary, therefore the minimal change solution was implemented.
         */
        return executionPlan.getContents().getFilteredTasks();
    }

    private void fireWhenReady() {
        // We know that we're running single-threaded here, so we can use coarse grained project locks
        gradleInternal.getOwner().getProjects().withMutableStateOfAllProjects(
            () -> buildOperationRunner.run(
                new NotifyTaskGraphWhenReady(DefaultTaskExecutionGraph.this, graphListeners.getSource(), gradleInternal)
            )
        );
    }

    private static class NotifyTaskGraphWhenReady implements RunnableBuildOperation {

        private final TaskExecutionGraph taskExecutionGraph;
        private final TaskExecutionGraphListener graphListener;
        private final GradleInternal gradleInternal;

        private NotifyTaskGraphWhenReady(TaskExecutionGraph taskExecutionGraph, TaskExecutionGraphListener graphListener, GradleInternal gradleInternal) {
            this.taskExecutionGraph = taskExecutionGraph;
            this.graphListener = graphListener;
            this.gradleInternal = gradleInternal;
        }

        @Override
        public void run(BuildOperationContext context) {
            graphListener.graphPopulated(taskExecutionGraph);
            context.setResult(NotifyTaskGraphWhenReadyBuildOperationType.RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(
                    gradleInternal.contextualize("Notify task graph whenReady listeners"))
                .details(
                    new NotifyTaskGraphWhenReadyDetails(
                        gradleInternal.getIdentityPath()
                    )
                );
        }
    }

    private static class NotifyTaskGraphWhenReadyDetails implements NotifyTaskGraphWhenReadyBuildOperationType.Details {

        private final Path buildPath;

        NotifyTaskGraphWhenReadyDetails(Path buildPath) {
            this.buildPath = buildPath;
        }

        @Override
        public String getBuildPath() {
            return buildPath.getPath();
        }

    }
}
