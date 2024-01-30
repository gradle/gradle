/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * An immutable snapshot of the currently scheduled nodes, together with the information about which nodes are entry points.
 */
public class ScheduledWork implements QueryableExecutionPlan.ScheduledNodes {
    private final ImmutableList<Node> scheduledNodes;
    private final ImmutableSet<Node> entryNodes;

    public ScheduledWork(List<? extends Node> scheduledNodes, Collection<? extends Node> entryNodes) {
        // Checking that entryNodes are a subset of scheduledNodes can be expensive, so it is omitted from here.
        this.scheduledNodes = ImmutableList.copyOf(scheduledNodes);
        this.entryNodes = ImmutableSet.copyOf(entryNodes);
    }

    @Override
    public void visitNodes(BiConsumer<List<Node>, Set<Node>> visitor) {
        visitor.accept(scheduledNodes, entryNodes);
    }

    /**
     * Returns the list of the scheduled nodes in the scheduling order.
     *
     * @return the list of the scheduled nodes
     */
    public ImmutableList<Node> getScheduledNodes() {
        return scheduledNodes;
    }

    /**
     * Returns the set of the entry nodes. For example, this set may contain task nodes for the tasks specified in the command line.
     * The returned set may not be a subset of {@link #getScheduledNodes()}, if some entry tasks are not scheduled.
     *
     * @return the set of the entry nodes
     * @see QueryableExecutionPlan#getRequestedTasks()
     */
    public ImmutableSet<Node> getEntryNodes() {
        return entryNodes;
    }
}
