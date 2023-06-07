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
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.specs.Spec;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildWorkGraph;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class DefaultBuildController implements BuildController {
    private enum State {
        DiscoveringTasks, ReadyToRun, RunningTasks, Finished
    }

    private final BuildWorkGraph workGraph;
    private final Set<ExportedTaskNode> scheduled = new LinkedHashSet<>();
    private final Set<ExportedTaskNode> queuedForExecution = new LinkedHashSet<>();
    private final WorkerLeaseService workerLeaseService;

    private State state = State.DiscoveringTasks;

    public DefaultBuildController(BuildState build, WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
        this.workGraph = build.getWorkGraph().newWorkGraph();
    }

    @Override
    public void queueForExecution(ExportedTaskNode taskNode) {
        assertInState(State.DiscoveringTasks);
        queuedForExecution.add(taskNode);
    }

    @Override
    public void populateWorkGraph(Consumer<? super BuildLifecycleController.WorkGraphBuilder> action) {
        assertInState(State.DiscoveringTasks);
        workGraph.populateWorkGraph(action);
    }

    @Override
    public void addFilter(Spec<Task> filter) {
        assertInState(State.DiscoveringTasks);
        workGraph.addFilter(filter);
    }

    @Override
    public void addFinalization(BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization) {
        assertInState(State.DiscoveringTasks);
        workGraph.addFinalization(finalization);
    }

    @Override
    public boolean scheduleQueuedTasks() {
        assertInState(State.DiscoveringTasks);

        queuedForExecution.removeAll(scheduled);
        if (queuedForExecution.isEmpty()) {
            return false;
        }

        boolean added = workGraph.schedule(queuedForExecution);
        scheduled.addAll(queuedForExecution);
        queuedForExecution.clear();
        return added;
    }

    @Override
    public void finalizeWorkGraph() {
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
        workGraph.finalizeGraph();

        state = State.ReadyToRun;
    }

    @Override
    public void startExecution(ExecutorService executorService, Consumer<ExecutionResult<Void>> completionHandler) {
        assertInState(State.ReadyToRun);
        executorService.submit(new BuildOpRunnable(CurrentBuildOperationRef.instance().get(), completionHandler));
        state = State.RunningTasks;
    }

    @Override
    public void stop() {
        workGraph.stop();
    }

    private void assertInState(State expectedState) {
        if (state != expectedState) {
            throw new IllegalStateException("Build is in unexpected state: " + state);
        }
    }

    private static void checkForCyclesFor(TaskInternal task, Set<TaskInternal> visited, Set<TaskInternal> visiting) {
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

            DirectedGraphRenderer<TaskInternal> graphRenderer = new DirectedGraphRenderer<>((node, output) -> output.withStyle(StyledTextOutput.Style.Identifier).text(node.getIdentityPath()), (node, values, connectedNodes) -> visitDependenciesOf(node, dep -> {
                if (cycle.contains(dep)) {
                    connectedNodes.add(dep);
                }
            }));
            StringWriter writer = new StringWriter();
            graphRenderer.renderTo(task, writer);
            throw new CircularReferenceException(String.format("Circular dependency between the following tasks:%n%s", writer));
        }
        visitDependenciesOf(task, dep -> checkForCyclesFor(dep, visited, visiting));
        visiting.remove(task);
        visited.add(task);
    }

    private static void visitDependenciesOf(TaskInternal task, Consumer<TaskInternal> consumer) {
        TaskNodeFactory taskNodeFactory = ((GradleInternal) task.getProject().getGradle()).getServices().get(TaskNodeFactory.class);
        TaskNode node = taskNodeFactory.getOrCreateNode(task);
        for (Node dependency : node.getAllSuccessors()) {
            if (dependency instanceof TaskNode) {
                consumer.accept(((TaskNode) dependency).getTask());
            }
        }
    }

    private ExecutionResult<Void> doRun() {
        try {
            return workerLeaseService.runAsWorkerThread(workGraph::runWork);
        } catch (Throwable t) {
            return ExecutionResult.failed(t);
        }
    }

    private class BuildOpRunnable implements Runnable {
        private final BuildOperationRef parentBuildOperation;
        private final Consumer<ExecutionResult<Void>> completionHandler;

        BuildOpRunnable(BuildOperationRef parentBuildOperation, Consumer<ExecutionResult<Void>> completionHandler) {
            this.parentBuildOperation = parentBuildOperation;
            this.completionHandler = completionHandler;
        }

        @Override
        public void run() {
            CurrentBuildOperationRef.instance().set(parentBuildOperation);
            try {
                completionHandler.accept(doRun());
            } finally {
                CurrentBuildOperationRef.instance().set(null);
            }
        }
    }
}
