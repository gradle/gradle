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

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Represents a graph of dependent work items, returned in execution order. The methods of this interface are not thread safe.
 */
@NotThreadSafe
public interface ExecutionPlan extends Describable, Closeable {

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
        public void finalizePlan() {
            throw new IllegalStateException();
        }

        @Override
        public WorkSource<Node> asWorkSource() {
            throw new IllegalStateException();
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
        public ScheduledNodes getScheduledNodes() {
            return visitor -> visitor.accept(Collections.emptyList());
        }

        @Override
        public Set<Task> getFilteredTasks() {
            return Collections.emptySet();
        }

        @Override
        public void onComplete(Consumer<LocalTaskNode> handler) {
            throw new IllegalStateException();
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
     * Returns the node for the supplied task that is part of this execution plan.
     *
     * @throws IllegalStateException When no node for the supplied task is part of this execution plan.
     */
    TaskNode getNode(Task task);

    void addNodes(Collection<? extends Node> nodes);

    void addEntryTasks(Collection<? extends Task> tasks);

    void addEntryTasks(Collection<? extends Task> tasks, int ordinal);

    /**
     * Calculates the execution plan for the current entry tasks. May be called multiple times.
     */
    void determineExecutionPlan();

    /**
     * Finalizes this plan once all nodes have been added. Must be called after {@link #determineExecutionPlan()}.
     */
    void finalizePlan();

    /**
     * Returns this plan as a {@link WorkSource} ready for execution. Must be called after {@link #finalizePlan()}.
     */
    WorkSource<Node> asWorkSource();

    /**
     * @return The set of all available tasks. This includes tasks that have not yet been executed, as well as tasks that have been processed.
     */
    Set<Task> getTasks();

    /**
     * Returns a snapshot of the requested tasks for this plan.
     */
    Set<Task> getRequestedTasks();

    /**
     * Returns a snapshot of the current set of scheduled nodes, which can later be visited.
     */
    ScheduledNodes getScheduledNodes();

    /**
     * Returns a snapshot of the filtered tasks for this plan.
     */
    Set<Task> getFilteredTasks();

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
     * An immutable snapshot of the set of scheduled nodes.
     */
    interface ScheduledNodes {
        void visitNodes(Consumer<List<Node>> visitor);
    }
}
