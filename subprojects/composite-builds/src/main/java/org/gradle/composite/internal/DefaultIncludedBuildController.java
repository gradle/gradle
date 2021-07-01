/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.BuildResult;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.InternalListener;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.gradle.composite.internal.IncludedBuildTaskResource.State.FAILED;
import static org.gradle.composite.internal.IncludedBuildTaskResource.State.SUCCESS;
import static org.gradle.composite.internal.IncludedBuildTaskResource.State.WAITING;

class DefaultIncludedBuildController extends AbstractIncludedBuildController implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuildController.class);
    private final IncludedBuildState includedBuild;
    private final ResourceLockCoordinationService coordinationService;
    private final ProjectStateRegistry projectStateRegistry;

    private enum State {
        QueueingTasks, ReadyToRun, RunningTasks
    }

    // Fields guarded by lock
    private final Lock lock = new ReentrantLock();
    private final Condition stateChange = lock.newCondition();
    private final Map<String, TaskState> tasks = new LinkedHashMap<>();
    private final Set<String> tasksAdded = new HashSet<>();
    private final List<Throwable> taskFailures = new ArrayList<>();
    private State state = State.ReadyToRun;

    public DefaultIncludedBuildController(IncludedBuildState includedBuild, ResourceLockCoordinationService coordinationService, ProjectStateRegistry projectStateRegistry) {
        this.includedBuild = includedBuild;
        this.coordinationService = coordinationService;
        this.projectStateRegistry = projectStateRegistry;
    }

    @Override
    public boolean populateTaskGraph() {
        Set<String> tasksToExecute = new LinkedHashSet<>();
        lock.lock();
        try {
            if (state == State.ReadyToRun) {
                // Nothing left to schedule
                return false;
            }
            assertBuildInState(State.QueueingTasks);
            for (Map.Entry<String, TaskState> taskEntry : tasks.entrySet()) {
                if (taskEntry.getValue().status == TaskStatus.QUEUED) {
                    String taskName = taskEntry.getKey();
                    if (tasksAdded.add(taskName)) {
                        tasksToExecute.add(taskName);
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        includedBuild.scheduleTasks(tasksToExecute);

        setState(State.ReadyToRun);

        lock.lock();
        try {
            for (Map.Entry<String, TaskState> entry : tasks.entrySet()) {
                TaskState taskState = entry.getValue();
                if (taskState.task == null) {
                    taskState.task = doGetTask(entry.getKey());
                }
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    @Override
    public void validateTaskGraph() {
        lock.lock();
        try {
            assertBuildInState(State.ReadyToRun);
            // TODO - This check should live in the task execution plan, so that it can reuse checks that have already been performed and
            //   also check for cycles across all nodes
            Set<TaskInternal> visited = new HashSet<>();
            Set<TaskInternal> visiting = new HashSet<>();
            for (Map.Entry<String, TaskState> entry : tasks.entrySet()) {
                checkForCyclesFor(entry.getValue().task, visited, visiting);
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkForCyclesFor(TaskInternal task, Set<TaskInternal> visited, Set<TaskInternal> visiting) {
        if (visited.contains(task)) {
            // Already checked
            return;
        }
        if (!visiting.add(task)) {
            // Visiting dependencies -> have found a cycle
            CachingDirectedGraphWalker<TaskInternal, Void> graphWalker = new CachingDirectedGraphWalker<>((node, values, connectedNodes) -> visitDependenciesOf(node, connectedNodes::add));
            graphWalker.add(task);
            List<Set<TaskInternal>> cycles = graphWalker.findCycles();
            Set<TaskInternal> cycle = cycles.get(0);

            DirectedGraphRenderer<TaskInternal> graphRenderer = new DirectedGraphRenderer<>(
                (node, output) -> output.withStyle(StyledTextOutput.Style.Identifier).text(node.getIdentityPath()),
                (node, values, connectedNodes) -> visitDependenciesOf(node, dep -> {
                    if (cycle.contains(dep)) {
                        connectedNodes.add(dep);
                    }
                })
            );
            StringWriter writer = new StringWriter();
            graphRenderer.renderTo(task, writer);
            throw new CircularReferenceException(String.format("Circular dependency between the following tasks:%n%s", writer.toString()));
        }
        visitDependenciesOf(task, dep -> checkForCyclesFor(dep, visited, visiting));
        visiting.remove(task);
        visited.add(task);
    }

    private void visitDependenciesOf(TaskInternal task, Consumer<TaskInternal> consumer) {
        TaskNodeFactory taskNodeFactory = ((GradleInternal) task.getProject().getGradle()).getServices().get(TaskNodeFactory.class);
        TaskNode node = taskNodeFactory.getOrCreateNode(task);
        for (Node dependency : node.getAllSuccessors()) {
            if (dependency instanceof TaskNode) {
                consumer.accept(((TaskNode) dependency).getTask());
            }
        }
    }

    @Override
    public void startTaskExecution(ExecutorService executorService) {
        lock.lock();
        try {
            assertBuildInState(State.ReadyToRun);
            state = State.RunningTasks;
            stateChange.signalAll();
        } finally {
            lock.unlock();
        }
        executorService.submit(new BuildOpRunnable(CurrentBuildOperationRef.instance().get()));
    }

    private void assertBuildInState(State expectedState) {
        if (state != expectedState) {
            throw new IllegalStateException("Build " + includedBuild.getName() + " is in wrong state. Expected: " + expectedState + " actual: " + state);
        }
    }

    @Override
    public void awaitTaskCompletion(Consumer<? super Throwable> taskFailures) {
        // Ensure that this thread does not hold locks while waiting and so prevent this work from completing
        projectStateRegistry.blocking(() -> {
            lock.lock();
            try {
                while (state == State.RunningTasks) {
                    awaitStateChange();
                }
                for (Throwable taskFailure : this.taskFailures) {
                    taskFailures.accept(taskFailure);
                }
                this.taskFailures.clear();
            } finally {
                lock.unlock();
            }
        });
    }

    private TaskInternal doGetTask(String taskPath) {
        for (Task task : getTaskGraph().getAllTasks()) {
            if (task.getPath().equals(taskPath)) {
                return (TaskInternal) task;
            }
        }
        throw includedBuildTaskWasNeverScheduled(taskPath);
    }

    private TaskExecutionGraphInternal getTaskGraph() {
        return includedBuild.getBuild().getTaskGraph();
    }

    @Override
    public void stop() {
        ArrayList<Throwable> failures = new ArrayList<>();
        awaitTaskCompletion(failures::add);
        if (!failures.isEmpty()) {
            throw new MultipleBuildFailures(failures);
        }
    }

    private void run() {
        try {
            Set<String> tasksToExecute = getQueuedTasks();
            if (tasksToExecute.isEmpty()) {
                return;
            }
            doBuild(tasksToExecute);
        } finally {
            setState(State.ReadyToRun);
        }
    }

    private void setState(State state) {
        lock.lock();
        try {
            this.state = state;
            stateChange.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private Set<String> getQueuedTasks() {
        lock.lock();
        try {
            Set<String> tasksToExecute = new LinkedHashSet<>();
            for (Map.Entry<String, TaskState> taskEntry : tasks.entrySet()) {
                TaskState taskState = taskEntry.getValue();
                if (taskState.status == TaskStatus.QUEUED) {
                    String taskPath = taskEntry.getKey();
                    tasksToExecute.add(taskPath);
                    taskState.status = TaskStatus.EXECUTING;
                }
            }
            return tasksToExecute;
        } finally {
            lock.unlock();
        }
    }

    private void awaitStateChange() {
        try {
            stateChange.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void doBuild(Collection<String> tasksToExecute) {
        LOGGER.info("Executing {} tasks {}", includedBuild.getName(), tasksToExecute);
        IncludedBuildExecutionListener listener = new IncludedBuildExecutionListener(tasksToExecute);
        try {
            includedBuild.execute(listener);
            tasksDone(tasksToExecute, null);
        } catch (RuntimeException failure) {
            tasksDone(tasksToExecute, failure);
        }
    }

    private void taskCompleted(String task, Throwable failure) {
        lock.lock();
        try {
            TaskState taskState = tasks.get(task);
            if (taskState == null) {
                taskState = new TaskState();
                tasks.put(task, taskState);
            }
            taskState.status = failure == null ? TaskStatus.SUCCESS : TaskStatus.FAILED;
        } finally {
            lock.unlock();
        }
        // Notify threads that may be waiting on this task to complete.
        // This is required because although all builds may share the same coordination service, the 'something may have changed' event that is fired when a task in this build completes
        // happens before the state tracked here is updated, and so the worker threads in the consuming build may think the task has not completed and go back to sleep waiting for some
        // other event to happen, which may not. Signalling again here means that all worker threads in all builds will be woken up which can be expensive.
        // It would be much better to avoid duplicating the task state here and instead have the task executors communicate directly with each other, possibly via some abstraction
        // that represents the task outcome
        coordinationService.notifyStateChange();
    }

    private void tasksDone(Collection<String> tasksExecuted, @Nullable RuntimeException failure) {
        boolean someTasksNotCompleted = false;
        lock.lock();
        try {
            for (String task : tasksExecuted) {
                TaskState taskState = tasks.get(task);
                if (taskState.status == TaskStatus.EXECUTING) {
                    if (taskState.task.getState().getExecuted()) {
                        taskState.status = TaskStatus.SUCCESS;
                    } else {
                        taskState.status = TaskStatus.FAILED;
                        someTasksNotCompleted = true;
                    }
                }
            }
            if (failure != null) {
                if (failure instanceof MultipleBuildFailures) {
                    taskFailures.addAll(((MultipleBuildFailures) failure).getCauses());
                } else {
                    taskFailures.add(failure);
                }
            }
        } finally {
            lock.unlock();
        }
        if (someTasksNotCompleted) {
            // See the comment in #taskCompleted, above, for why this is here and why this is a problem
            coordinationService.notifyStateChange();
        }
    }

    @Override
    protected void queueForExecution(String taskPath) {
        lock.lock();
        try {
            if (state == State.RunningTasks) {
                throw new IllegalStateException();
            }
            if (!tasks.containsKey(taskPath)) {
                tasks.put(taskPath, new TaskState());
            }
            setState(State.QueueingTasks);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected IncludedBuildTaskResource.State getTaskState(String taskPath) {
        lock.lock();
        try {
            TaskState state = tasks.get(taskPath);
            if (state == null) {
                throw includedBuildTaskWasNeverScheduled(taskPath);
            }
            if (state.status == TaskStatus.FAILED) {
                return FAILED;
            }
            if (state.status == TaskStatus.SUCCESS) {
                return SUCCESS;
            }
            return WAITING;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected TaskInternal getTask(String taskPath) {
        lock.lock();
        try {
            TaskState state = tasks.get(taskPath);
            if (state == null) {
                throw includedBuildTaskWasNeverScheduled(taskPath);
            }
            if (state.task == null) {
                throw new IllegalStateException();
            }
            return state.task;
        } finally {
            lock.unlock();
        }
    }

    private IllegalStateException includedBuildTaskWasNeverScheduled(String taskPath) {
        return new IllegalStateException("Included build task '" + taskPath + "' was never scheduled for execution.");
    }

    private enum TaskStatus {QUEUED, EXECUTING, FAILED, SUCCESS}

    private static class TaskState {
        public BuildResult result;
        public TaskStatus status = TaskStatus.QUEUED;
        public TaskInternal task;
    }

    private class BuildOpRunnable implements Runnable {
        private final BuildOperationRef parentBuildOperation;

        BuildOpRunnable(BuildOperationRef parentBuildOperation) {
            this.parentBuildOperation = parentBuildOperation;
        }

        @Override
        public void run() {
            CurrentBuildOperationRef.instance().set(parentBuildOperation);
            try {
                DefaultIncludedBuildController.this.run();
            } finally {
                CurrentBuildOperationRef.instance().set(null);
            }
        }
    }

    private class IncludedBuildExecutionListener implements TaskExecutionGraphListener, TaskListenerInternal, InternalListener {
        private final Collection<String> tasksToExecute;

        IncludedBuildExecutionListener(Collection<String> tasksToExecute) {
            this.tasksToExecute = tasksToExecute;
        }

        @Override
        public void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
            for (String task : tasksToExecute) {
                if (!taskExecutionGraph.hasTask(task)) {
                    throw new GradleException("Task '" + task + "' not found in build '" + includedBuild.getName() + "'.");
                }
            }
        }

        @Override
        public void beforeExecute(TaskIdentity<?> taskIdentity) {
        }

        @Override
        public void afterExecute(TaskIdentity<?> taskIdentity, org.gradle.api.tasks.TaskState state) {
            Throwable failure = state.getFailure();
            taskCompleted(taskIdentity.getTaskPath(), failure);
        }
    }
}
