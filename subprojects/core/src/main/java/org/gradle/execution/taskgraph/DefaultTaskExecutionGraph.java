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
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.TaskExecutionGraphInternal;
import org.gradle.execution.workgraph.TaskNode;
import org.gradle.execution.workgraph.WorkGraph;
import org.gradle.execution.workgraph.WorkGraphBuilder;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.graph.GraphNodeRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.scheduler.CycleReporter;
import org.gradle.internal.scheduler.Edge;
import org.gradle.internal.scheduler.Graph;
import org.gradle.internal.scheduler.GraphExecutionResult;
import org.gradle.internal.scheduler.Node;
import org.gradle.internal.scheduler.NodeExecutor;
import org.gradle.internal.scheduler.Scheduler;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF;
import static org.gradle.internal.scheduler.EdgeType.FINALIZED_BY;
import static org.gradle.internal.scheduler.EdgeType.MUST_COMPLETE_BEFORE;

@NonNullApi
public class DefaultTaskExecutionGraph implements TaskExecutionGraphInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskExecutionGraph.class);

    // This currently needs to be lazy, as it uses state that is not available when the graph is created
    private final Factory<? extends TaskExecuter> taskExecuter;
    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionListener> taskListeners;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Factory<? extends Scheduler> scheduler;
    private final WorkGraphBuilder workGraphBuilder = new WorkGraphBuilder();
    private WorkGraph workGraph;
    private boolean continueOnFailure;

    public DefaultTaskExecutionGraph(ListenerManager listenerManager, Factory<? extends TaskExecuter> taskExecuter, BuildOperationExecutor buildOperationExecutor, Factory<? extends Scheduler> scheduler) {
        this.taskExecuter = taskExecuter;
        this.buildOperationExecutor = buildOperationExecutor;
        this.graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
        this.taskListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
        this.scheduler = scheduler;
    }

    @Override
    public void setContinueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
    }

    @Override
    public void useFilter(@Nullable final Spec<? super Task> filter) {
        workGraphBuilder.setFilter(filter);
        workGraph = null;
    }

    @Override
    public void addTasks(Iterable<? extends Task> tasks) {
        workGraphBuilder.addTasks(tasks);
        workGraph = null;
    }

    @Override
    public void populate() {
        getWorkGraph();
    }

    private WorkGraph getWorkGraph() {
        if (workGraph == null) {
            Timer clock = Time.startTimer();
            workGraph = workGraphBuilder.build(new CycleReporter() {
                @Override
                public String reportCycle(final Graph graph, Collection<Node> cycle) {
                    final List<Node> sortedCycle = Lists.newArrayList(cycle);
                    Collections.sort(sortedCycle, new Comparator<Node>() {
                        @Override
                        public int compare(Node o1, Node o2) {
                            return ((TaskNode) o1).getTask().compareTo(((TaskNode) o2).getTask());
                        }
                    });
                    DirectedGraphRenderer<Node> graphRenderer = new DirectedGraphRenderer<Node>(new GraphNodeRenderer<Node>() {
                        @Override
                        public void renderTo(Node node, StyledTextOutput output) {
                            output.withStyle(StyledTextOutput.Style.Identifier).text(node);
                        }
                    }, new DirectedGraph<Node, Object>() {
                        @Override
                        public void getNodeValues(Node node, Collection<? super Object> values, Collection<? super Node> connectedNodes) {
                            for (Node dependency : sortedCycle) {
                                for (Edge incoming : graph.getIncomingEdges(node)) {
                                    if ((incoming.getType() == DEPENDENCY_OF || incoming.getType() == MUST_COMPLETE_BEFORE)
                                        && incoming.getSource() == dependency) {
                                        connectedNodes.add(dependency);
                                    }
                                }
                                for (Edge outgoing : graph.getOutgoingEdges(node)) {
                                    if (outgoing.getType() == FINALIZED_BY && outgoing.getTarget() == dependency) {
                                        connectedNodes.add(dependency);
                                    }
                                }
                            }
                        }
                    });
                    StringWriter writer = new StringWriter();
                    Node firstNode = sortedCycle.get(0);
                    graphRenderer.renderTo(firstNode, writer);
                    return writer.toString();
                }
            });
            LOGGER.debug("Timing: Creating the DAG took " + clock.getElapsed());
        }
        return workGraph;
    }

    @Override
    public void execute(Collection<? super Throwable> taskFailures) {
        Timer clock = Time.startTimer();
        WorkGraph workGraph = getWorkGraph();

        graphListeners.getSource().graphPopulated(this);
        TaskExecuter taskExecuter = this.taskExecuter.create();
        assert taskExecuter != null;
        Scheduler scheduler = this.scheduler.create();
        assert scheduler != null;
        NodeExecutor nodeExecutor = new TaskNodeExecutor(taskExecuter, buildOperationExecutor.getCurrentOperation());
        GraphExecutionResult result = scheduler.execute(workGraph.getGraph(), workGraph.getRequestedNodes(), continueOnFailure, nodeExecutor);
        LOGGER.debug("Timing: Executing the DAG took " + clock.getElapsed());
        taskFailures.addAll(result.getFailures());
    }

    @Override
    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(listener);
    }

    @Override
    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    @Override
    public void whenReady(final Closure closure) {
        graphListeners.add(new ClosureBackedMethodInvocationDispatch("graphPopulated", closure));
    }

    @Override
    public void whenReady(final Action<TaskExecutionGraph> action) {
        graphListeners.add(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph graph) {
                action.execute(graph);
            }
        });
    }

    @Override
    public void addTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.add(listener);
    }

    @Override
    public void removeTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.remove(listener);
    }

    @Override
    public void beforeTask(final Closure closure) {
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("beforeExecute", closure));
    }

    @Override
    public void beforeTask(final Action<Task> action) {
        taskListeners.add(new TaskExecutionAdapter() {
            @Override
            public void beforeExecute(Task task) {
                action.execute(task);
            }
        });
    }

    @Override
    public void afterTask(final Closure closure) {
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("afterExecute", closure));
    }

    @Override
    public void afterTask(final Action<Task> action) {
        taskListeners.add(new TaskExecutionAdapter() {
            @Override
            public void afterExecute(Task task, TaskState state) {
                action.execute(task);
            }
        });
    }

    @Override
    public boolean hasTask(Task task) {
        return getWorkGraph().hasTask(task);
    }

    @Override
    public boolean hasTask(String path) {
        return getWorkGraph().hasTask(path);
    }

    @Override
    public List<Task> getAllTasks() {
        return ImmutableList.copyOf(getWorkGraph().getAllTasks());
    }

    @Override
    public Set<Task> getDependencies(Task task) {
        return getWorkGraph().getDirectTaskDependencies(task);
    }

    /**
     * This action executes a task via the task executer wrapping everything into a build operation.
     */
    private class TaskNodeExecutor implements NodeExecutor {
        private final TaskExecuter taskExecuter;
        private final BuildOperationRef parentOperation;

        public TaskNodeExecutor(TaskExecuter taskExecuter, BuildOperationRef parentOperation) {
            this.taskExecuter = taskExecuter;
            this.parentOperation = parentOperation;
        }

        @Nullable
        @Override
        public Throwable execute(Node node) {
            if (!(node instanceof TaskNode)) {
                throw new IllegalArgumentException("Only knows tasks at this point");
            }
            TaskInternal task = ((TaskNode) node).getTask();
            BuildOperationRef previous = CurrentBuildOperationRef.instance().get();
            CurrentBuildOperationRef.instance().set(parentOperation);
            try {
                TaskStateInternal state = task.getState();
                TaskExecutionContext ctx = new DefaultTaskExecutionContext();
                taskExecuter.execute(task, state, ctx);
                return state.getFailure();
            } finally {
                CurrentBuildOperationRef.instance().set(previous);
            }
        }
    }

    @Override
    public Set<Task> getRequestedTasks() {
        return getWorkGraph().getRequestedTasks();
    }

    @Override
    public Set<Task> getFilteredTasks() {
        /*
            Note: we currently extract this information from the execution plan because it's
            buried under functions in #filter. This could be detangled/simplified by introducing
            excludeTasks(Iterable<Task>) as an analog to addTasks(Iterable<Task>).

            This is too drastic a change for the stage in the release cycle were exposing this information
            was necessary, therefore the minimal change solution was implemented.
         */
        return getWorkGraph().getFilteredTasks();
    }

    @Override
    public TaskExecutionListener getTaskExecutionListenerSource() {
        return taskListeners.getSource();
    }
}
