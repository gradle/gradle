/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Represents a graph of dependent work items, returned in execution order.
 */
public interface ExecutionPlan extends Describable, Closeable {
    enum State {
        /**
         * There may be nodes ready to start. The worker thread should call {@link #selectNext()} to select the next node.
         * Note this does not mean that {@link #selectNext()} will necessarily return a node, only that it is likely to.
         * {@link #selectNext()} may not return a node, for example when some other worker thread takes the work.
         */
        MaybeNodesReadyToStart,
        /**
         * No nodes are ready to start, but there are nodes still queued to start. The worker thread should wait for a change and check again.
         */
        NoNodesReadyToStart,
        /**
         * All nodes have started (but not necessarily finished) and there are no further nodes to start. The worker thread should finish.
         * Note that this does not mean that all work has completed.
         */
        NoMoreNodesToStart
    }

    abstract class NodeSelection {
        public static NodeSelection of(Node node) {
            return new NodeSelection() {
                @Override
                public Node getNode() {
                    return node;
                }
            };
        }

        public Node getNode() {
            throw new IllegalStateException();
        }
    }

    NodeSelection NO_NODES_READY_TO_START = new NodeSelection() {
    };
    NodeSelection NO_MORE_NODES_TO_START = new NodeSelection() {
    };

    ExecutionPlan EMPTY = new ExecutionPlan() {
        @Override
        public void useFilter(Spec<? super Task> filter) {
            throw new IllegalStateException();
        }

        @Override
        public void setContinueOnFailure(boolean continueOnFailure) {
            throw new IllegalStateException();
        }

        @Override
        public State executionState() {
            return State.NoMoreNodesToStart;
        }

        @Override
        public NodeSelection selectNext() {
            return NO_MORE_NODES_TO_START;
        }

        @Override
        public void finishedExecuting(Node node) {
            throw new IllegalStateException();
        }

        @Override
        public void abortAllAndFail(Throwable t) {
        }

        @Override
        public void cancelExecution() {
        }

        @Override
        public TaskNode getNode(Task task) {
            throw new IllegalStateException();
        }

        @Override
        public void addNodes(Collection<? extends Node> nodes) {
            throw new IllegalStateException();
        }

        @Override
        public void addEntryTasks(Collection<? extends Task> tasks) {
            throw new IllegalStateException();
        }

        @Override
        public void addEntryTasks(Collection<? extends Task> tasks, int ordinal) {
            throw new IllegalStateException();
        }

        @Override
        public void determineExecutionPlan() {
        }

        @Override
        public Set<Task> getTasks() {
            return Collections.emptySet();
        }

        @Override
        public Set<Task> getRequestedTasks() {
            return Collections.emptySet();
        }

        @Override
        public List<Node> getScheduledNodes() {
            return Collections.emptyList();
        }

        @Override
        public List<Node> getScheduledNodesPlusDependencies() {
            return Collections.emptyList();
        }

        @Override
        public Set<Task> getFilteredTasks() {
            return Collections.emptySet();
        }

        @Override
        public void collectFailures(Collection<? super Throwable> failures) {
        }

        @Override
        public void onComplete(Consumer<LocalTaskNode> handler) {
            throw new IllegalStateException();
        }

        @Override
        public boolean allExecutionComplete() {
            return true;
        }

        @Override
        public Diagnostics healthDiagnostics() {
            return new Diagnostics(true, Collections.emptyList());
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public String getDisplayName() {
            return "empty";
        }

        @Override
        public void close() {
        }
    };

    void useFilter(Spec<? super Task> filter);

    void setContinueOnFailure(boolean continueOnFailure);

    /**
     * Returns the current execution state of this plan.
     *
     * <p>Note: the caller does not need to hold a worker lease to call this method.</p>
     *
     * <p>The implementation of this method may prefer to return {@link ExecutionPlan.State#NO_NODES_READY_TO_START} in certain cases, to limit
     * the amount of work that happens in this method, which is called many, many times and should be fast.</p>
     */
    State executionState();

    /**
     * Selects a node to start, returns {@link #NO_NODES_READY_TO_START} when there are no nodes that are ready to start (but some are queued for execution)
     * and {@link #NO_MORE_NODES_TO_START} when there are no nodes remaining to start.
     *
     * <p>Note: the caller must hold a worker lease.</p>
     *
     * <p>The caller must call {@link #finishedExecuting(Node)} when execution is complete.</p>
     */
    NodeSelection selectNext();

    void finishedExecuting(Node node);

    void abortAllAndFail(Throwable t);

    void cancelExecution();

    /**
     * Returns some diagnostic information about the state of this plan. This is used to monitor the health of the plan.
     */
    Diagnostics healthDiagnostics();

    /**
     * Returns the node for the supplied task that is part of this execution plan.
     *
     * @throws IllegalStateException When no node for the supplied task is part of this execution plan.
     */
    TaskNode getNode(Task task);

    void addNodes(Collection<? extends Node> nodes);

    void addEntryTasks(Collection<? extends Task> tasks);

    void addEntryTasks(Collection<? extends Task> tasks, int ordinal);

    void determineExecutionPlan();

    /**
     * @return The set of all available tasks. This includes tasks that have not yet been executed, as well as tasks that have been processed.
     */
    Set<Task> getTasks();

    Set<Task> getRequestedTasks();

    List<Node> getScheduledNodes();

    List<Node> getScheduledNodesPlusDependencies();

    /**
     * @return The set of all filtered tasks that don't get executed.
     */
    Set<Task> getFilteredTasks();

    /**
     * Collects the current set of task failures into the given collection.
     */
    void collectFailures(Collection<? super Throwable> failures);

    /**
     * Has all execution completed?
     *
     * <p>When this method returns {@code true}, there is definitely no further work to start and no work in progress.</p>
     *
     * <p>When this method returns {@code false}, there may be further work yet to complete.</p>
     */
    boolean allExecutionComplete();

    /**
     * Returns the number of work items in the plan.
     */
    int size();

    /**
     * Invokes the given action when a task completes (as per {@link Node#isComplete()}). Does nothing for tasks that have already completed.
     */
    void onComplete(Consumer<LocalTaskNode> handler);

    @Override
    void close();

    /**
     * Some basic diagnostic information about the state of the plan.
     */
    class Diagnostics {
        private final boolean canMakeProgress;
        private final List<String> queuedNodes;

        public Diagnostics(boolean canMakeProgress, List<String> queuedNodes) {
            this.canMakeProgress = canMakeProgress;
            this.queuedNodes = queuedNodes;
        }

        /**
         * Returns true when this plan is either finished or is still able to select further nodes.
         * Returns false when there are nodes queued but none of them will be able to be selected, without some external change (eg completion of a node in an included build).
         *
         * this method should never return false.
         */
        public boolean canMakeProgress() {
            return canMakeProgress;
        }

        /**
         * A description of each queued node. Is empty when {@link #canMakeProgress()} returns true (as this information is not required in that case).
         */
        public List<String> getQueuedNodes() {
            return queuedNodes;
        }
    }
}
