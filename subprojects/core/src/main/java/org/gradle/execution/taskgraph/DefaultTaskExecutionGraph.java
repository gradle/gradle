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
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.BuildScopeListenerRegistrationListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskState;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.execution.ProjectExecutionServiceRegistry;
import org.gradle.execution.plan.DefaultExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.NodeExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@NonNullApi
public class DefaultTaskExecutionGraph implements TaskExecutionGraphInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskExecutionGraph.class);

    private enum GraphState {
        EMPTY, DIRTY, POPULATED
    }

    private final PlanExecutor planExecutor;
    private final ResourceLockCoordinationService coordinationService;
    private final List<NodeExecutor> nodeExecutors;
    private final GradleInternal gradleInternal;
    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionListener> taskListeners;
    private final BuildScopeListenerRegistrationListener buildScopeListenerRegistrationListener;
    private final ProjectStateRegistry projectStateRegistry;
    private final ServiceRegistry globalServices;
    private final DefaultExecutionPlan executionPlan;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ListenerBuildOperationDecorator listenerBuildOperationDecorator;
    private GraphState graphState = GraphState.EMPTY;
    private List<Task> allTasks;
    private boolean hasFiredWhenReady;

    private final Set<Task> requestedTasks = Sets.newTreeSet();

    public DefaultTaskExecutionGraph(
        PlanExecutor planExecutor,
        List<NodeExecutor> nodeExecutors,
        BuildOperationExecutor buildOperationExecutor,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator,
        ResourceLockCoordinationService coordinationService,
        GradleInternal gradleInternal,
        TaskNodeFactory taskNodeFactory,
        TaskDependencyResolver dependencyResolver,
        ListenerBroadcast<TaskExecutionGraphListener> graphListeners,
        ListenerBroadcast<TaskExecutionListener> taskListeners,
        BuildScopeListenerRegistrationListener buildScopeListenerRegistrationListener,
        ProjectStateRegistry projectStateRegistry,
        ServiceRegistry globalServices
    ) {
        this.planExecutor = planExecutor;
        this.nodeExecutors = nodeExecutors;
        this.buildOperationExecutor = buildOperationExecutor;
        this.listenerBuildOperationDecorator = listenerBuildOperationDecorator;
        this.coordinationService = coordinationService;
        this.gradleInternal = gradleInternal;
        this.graphListeners = graphListeners;
        this.taskListeners = taskListeners;
        this.buildScopeListenerRegistrationListener = buildScopeListenerRegistrationListener;
        this.projectStateRegistry = projectStateRegistry;
        this.globalServices = globalServices;
        this.executionPlan = new DefaultExecutionPlan(gradleInternal, taskNodeFactory, dependencyResolver);
    }

    @Override
    public void setContinueOnFailure(boolean continueOnFailure) {
        executionPlan.setContinueOnFailure(continueOnFailure);
    }

    @Override
    public void useFilter(Spec<? super Task> filter) {
        Spec<? super Task> castFilter = Cast.uncheckedNonnullCast(filter != null ? filter : Specs.SATISFIES_ALL);
        executionPlan.useFilter(castFilter);
        graphState = GraphState.DIRTY;
    }

    @Override
    public void addEntryTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        final Timer clock = Time.startTimer();

        Set<Task> taskSet = new LinkedHashSet<>();
        for (Task task : tasks) {
            taskSet.add(task);
            requestedTasks.add(task);
        }

        executionPlan.addEntryTasks(taskSet);
        graphState = GraphState.DIRTY;

        LOGGER.debug("Timing: Creating the DAG took " + clock.getElapsed());
    }

    @Override
    public void addNodes(Collection<? extends Node> nodes) {
        executionPlan.addNodes(nodes);
        graphState = GraphState.DIRTY;
    }

    @Override
    public void populate() {
        ensurePopulated();
        if (!hasFiredWhenReady) {
            // We know that we're running single-threaded here, so we can use coarse grained project locks
            projectStateRegistry.withMutableStateOfAllProjects(() -> buildOperationExecutor.run(new NotifyTaskGraphWhenReady(DefaultTaskExecutionGraph.this, graphListeners.getSource(), gradleInternal)));
            hasFiredWhenReady = true;
        } else if (!graphListeners.isEmpty()) {
            LOGGER.warn("Ignoring listeners of task graph ready event, as this build (" + gradleInternal.getIdentityPath() + ") has already executed work.");
        }
    }

    @Override
    public void execute(Collection<? super Throwable> failures) {
        if (!hasFiredWhenReady) {
            throw new IllegalStateException("Task graph should be populated before execution starts.");
        }
        try (ProjectExecutionServiceRegistry projectExecutionServices = new ProjectExecutionServiceRegistry(globalServices)) {
            executeWithServices(projectExecutionServices, failures);
        }
    }

    private void executeWithServices(ProjectExecutionServiceRegistry projectExecutionServices, Collection<? super Throwable> failures) {
        Timer clock = Time.startTimer();
        try {
            planExecutor.process(executionPlan, failures,
                new BuildOperationAwareExecutionAction(
                    buildOperationExecutor.getCurrentOperation(),
                    new InvokeNodeExecutorsAction(nodeExecutors, projectExecutionServices)
                )
            );
            LOGGER.debug("Timing: Executing the DAG took " + clock.getElapsed());
        } finally {
            coordinationService.withStateLock(resourceLockState -> {
                executionPlan.clear();
                return ResourceLockState.Disposition.FINISHED;
            });
        }
    }

    @Override
    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(listenerBuildOperationDecorator.decorate("TaskExecutionGraph.addTaskExecutionGraphListener", TaskExecutionGraphListener.class, listener));
    }

    @Override
    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    @Override
    public void whenReady(final Closure closure) {
        graphListeners.add(new ClosureBackedMethodInvocationDispatch("graphPopulated", listenerBuildOperationDecorator.decorate("TaskExecutionGraph.whenReady", Cast.<Closure<?>>uncheckedCast(closure))));
    }

    @Override
    public void whenReady(final Action<TaskExecutionGraph> action) {
        graphListeners.add(listenerBuildOperationDecorator.decorate("TaskExecutionGraph.whenReady", TaskExecutionGraphListener.class, action::execute));
    }

    @Override
    public void addTaskExecutionListener(TaskExecutionListener listener) {
        notifyListenerRegistration("TaskExecutionGraph.addTaskExecutionListener", listener);
        taskListeners.add(listener);
    }

    @Override
    public void removeTaskExecutionListener(TaskExecutionListener listener) {
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
        buildScopeListenerRegistrationListener.onBuildScopeListenerRegistration(
            listener,
            registrationPoint,
            this
        );
    }

    @Override
    public boolean hasTask(Task task) {
        ensurePopulated();
        return executionPlan.getTasks().contains(task);
    }

    @Override
    public boolean hasTask(String path) {
        ensurePopulated();
        for (Task task : executionPlan.getTasks()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return executionPlan.size();
    }

    @Override
    public List<Task> getAllTasks() {
        ensurePopulated();
        if (allTasks == null) {
            allTasks = ImmutableList.copyOf(executionPlan.getTasks());
        }
        return allTasks;
    }

    @Override
    public List<Node> getScheduledWork() {
        return executionPlan.getScheduledNodes();
    }

    @Override
    public Set<Task> getDependencies(Task task) {
        ensurePopulated();
        Node node = executionPlan.getNode(task);
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (Node dependencyNode : node.getDependencySuccessors()) {
            if (dependencyNode instanceof TaskNode) {
                builder.add(((TaskNode) dependencyNode).getTask());
            }
        }
        return builder.build();
    }

    private void ensurePopulated() {
        switch (graphState) {
            case EMPTY:
                throw new IllegalStateException(
                    "Task information is not available, as this task execution graph has not been populated.");
            case DIRTY:
                executionPlan.determineExecutionPlan();
                allTasks = null;
                graphState = GraphState.POPULATED;
                return;
            case POPULATED:
        }
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
            BuildOperationRef previous = CurrentBuildOperationRef.instance().get();
            CurrentBuildOperationRef.instance().set(parentOperation);
            try {
                delegate.execute(node);
            } finally {
                CurrentBuildOperationRef.instance().set(previous);
            }
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
    public Set<Task> getRequestedTasks() {
        return requestedTasks;
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
        return executionPlan.getFilteredTasks();
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
            return BuildOperationDescriptor.displayName(gradleInternal.contextualize("Notify task graph whenReady listeners"))
                .details(new NotifyTaskGraphWhenReadyBuildOperationType.DetailsImpl(
                    gradleInternal.getIdentityPath()
                ));
        }
    }
}
