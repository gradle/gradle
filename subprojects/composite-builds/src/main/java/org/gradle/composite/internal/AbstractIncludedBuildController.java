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

package org.gradle.composite.internal;

import org.gradle.api.CircularReferenceException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

abstract class AbstractIncludedBuildController implements IncludedBuildController, Stoppable {
    private enum State {
        DiscoveringTasks, ReadyToRun, RunningTasks, Finished
    }

    private final CompositeBuildParticipantBuildState build;
    private final Set<ExportedTaskNode> scheduled = new LinkedHashSet<>();
    private final Set<ExportedTaskNode> queuedForExecution = new LinkedHashSet<>();
    private State state = State.DiscoveringTasks;

    public AbstractIncludedBuildController(CompositeBuildParticipantBuildState build) {
        this.build = build;
    }

    @Override
    public ExportedTaskNode locateTask(TaskInternal task) {
        assertInState(State.DiscoveringTasks);
        return build.getWorkGraph().locateTask(task);
    }

    @Override
    public ExportedTaskNode locateTask(String taskPath) {
        assertInState(State.DiscoveringTasks);
        return build.getWorkGraph().locateTask(taskPath);
    }

    @Override
    public void queueForExecution(ExportedTaskNode taskNode) {
        assertInState(State.DiscoveringTasks);
        queuedForExecution.add(taskNode);
    }

    @Override
    public boolean populateTaskGraph() {
        // Can be called after validating task graph
        if (state == State.ReadyToRun) {
            return false;
        }
        assertInState(State.DiscoveringTasks);

        queuedForExecution.removeAll(scheduled);
        if (queuedForExecution.isEmpty()) {
            return false;
        }

        build.getWorkGraph().schedule(queuedForExecution);
        scheduled.addAll(queuedForExecution);
        queuedForExecution.clear();
        return true;
    }

    @Override
    public void prepareForExecution() {
        if (state == State.ReadyToRun) {
            return;
        }
        assertInState(State.DiscoveringTasks);
        if (!queuedForExecution.isEmpty()) {
            throw new IllegalStateException("Queued tasks have not been scheduled.");
        }

        // TODO - This check should live in the task execution plan, so that it can reuse checks that have already been performed and
        //   also check for cycles across all nodes
        Set<TaskInternal> visited = new HashSet<>();
        Set<TaskInternal> visiting = new HashSet<>();
        for (ExportedTaskNode node : scheduled) {
            checkForCyclesFor(node.getTask(), visited, visiting);
        }
        build.getWorkGraph().prepareForExecution(false);

        state = State.ReadyToRun;
    }

    @Override
    public void startTaskExecution(ExecutorService executorService) {
        assertInState(State.ReadyToRun);
        state = State.RunningTasks;
        if (!scheduled.isEmpty()) {
            doStartTaskExecution(executorService);
        }
    }

    @Override
    public ExecutionResult<Void> awaitTaskCompletion() {
        assertInState(State.RunningTasks);
        ExecutionResult<Void> result;
        if (!scheduled.isEmpty()) {
            List<Throwable> failures = new ArrayList<>();
            doAwaitTaskCompletion(failures::add);
            result = ExecutionResult.maybeFailed(failures);
        } else {
            result = ExecutionResult.succeeded();
        }
        scheduled.clear();
        state = State.Finished;
        return result;
    }

    @Override
    public void stop() {
        if (state == State.RunningTasks) {
            throw new IllegalStateException("Build is currently running tasks.");
        }
    }

    protected abstract void doStartTaskExecution(ExecutorService executorService);

    protected abstract void doAwaitTaskCompletion(Consumer<? super Throwable> taskFailures);

    private void assertInState(State expectedState) {
        if (state != expectedState) {
            throw new IllegalStateException("Build is in unexpected state: " + state);
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

}
