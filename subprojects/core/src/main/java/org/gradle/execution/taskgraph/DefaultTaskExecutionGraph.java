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
import org.gradle.api.tasks.TaskState;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskNode;
import org.gradle.internal.Cast;
import org.gradle.internal.InternalListener;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressWarnings("deprecation")
@NullMarked
public class DefaultTaskExecutionGraph implements TaskExecutionGraphInternal {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskExecutionGraph.class);

    private final GradleInternal gradleInternal;
    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionGraphExecutionListener> internalGraphListeners;
    private final ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> taskListeners;
    private final BuildScopeListenerRegistrationListener buildScopeListenerRegistrationListener;
    private final BuildOperationRunner buildOperationRunner;
    private final ListenerBuildOperationDecorator listenerBuildOperationDecorator;
    private @Nullable FinalizedExecutionPlan executionPlan;
    private List<Task> allTasks = Collections.emptyList();
    private boolean hasFiredWhenReady;

    public DefaultTaskExecutionGraph(
        BuildOperationRunner buildOperationRunner,
        ListenerBuildOperationDecorator listenerBuildOperationDecorator,
        GradleInternal gradleInternal,
        ListenerBroadcast<TaskExecutionGraphListener> graphListeners,
        ListenerBroadcast<TaskExecutionGraphExecutionListener> internalGraphListeners,
        ListenerBroadcast<org.gradle.api.execution.TaskExecutionListener> taskListeners,
        BuildScopeListenerRegistrationListener buildScopeListenerRegistrationListener
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.listenerBuildOperationDecorator = listenerBuildOperationDecorator;
        this.gradleInternal = gradleInternal;
        this.graphListeners = graphListeners;
        this.internalGraphListeners = internalGraphListeners;
        this.taskListeners = taskListeners;
        this.buildScopeListenerRegistrationListener = buildScopeListenerRegistrationListener;
    }

    @Override
    public void populate(FinalizedExecutionPlan plan) {
        // TODO: Only store the execution plan for as long as we are executing whenReady
        //  callbacks. We do not own the execution plan and should not store long-lived
        //  references to it.
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
    public void depopulate() {
        executionPlan = null;
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
    public void addExecutionListener(TaskExecutionGraphExecutionListener listener) {
        internalGraphListeners.add(listener);
    }

    @Override
    public void removeExecutionListener(TaskExecutionGraphExecutionListener listener) {
        internalGraphListeners.remove(listener);
    }

    @Override
    public TaskExecutionGraphExecutionListener getGraphExecutionListeners() {
        return internalGraphListeners.getSource();
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
        if (executionPlan == null) {
            // TODO: Deprecate calling this method before whenReady is called
            return false;
        }

        return executionPlan.getContents().getTasks().contains(task);
    }

    @Nullable
    @Override
    public Task findTask(String path) {
        if (executionPlan == null) {
            // TODO: Deprecate calling this method before whenReady is called
            return null;
        }

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
    public List<Task> getAllTasks() {
        return allTasks;
    }

    @Override
    public @Nullable FinalizedExecutionPlan getExecutionPlan() {
        return executionPlan;
    }

    @Override
    public Set<Task> getDependencies(Task task) {
        if (executionPlan == null) {
            throw new IllegalStateException("Task graph has not been populated yet.");
        }

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
        internalGraphListeners.removeAll();
        graphListeners.removeAll();
        taskListeners.removeAll();
        executionPlan = null;
        allTasks = Collections.emptyList();
    }

    @Override
    public org.gradle.api.execution.TaskExecutionListener getLegacyTaskListenerBroadcast() {
        return taskListeners.getSource();
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
            return buildPath.asString();
        }

    }

}
