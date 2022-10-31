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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.resources.ResourceLockCoordinationService;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import static com.google.common.collect.Sets.newIdentityHashSet;

/**
 * The mutation methods on this implementation are NOT threadsafe, and callers must synchronize access to these methods.
 */
@NonNullApi
public class DefaultExecutionPlan implements ExecutionPlan, QueryableExecutionPlan {
    private final Set<Node> entryNodes = new LinkedHashSet<>();
    private final NodeMapping nodeMapping = new NodeMapping();
    private final String displayName;
    private final TaskNodeFactory taskNodeFactory;
    private final TaskDependencyResolver dependencyResolver;
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchy destroyableHierarchy;
    private final ResourceLockCoordinationService lockCoordinator;
    private Spec<? super Task> filter = Specs.satisfyAll();
    private int order = 0;
    private boolean continueOnFailure;

    private final Set<Node> filteredNodes = newIdentityHashSet();
    private final Set<Node> finalizers = new LinkedHashSet<>();
    private final OrdinalNodeAccess ordinalNodeAccess;
    private Consumer<LocalTaskNode> completionHandler = localTaskNode -> {
    };

    private DefaultFinalizedExecutionPlan finalizedPlan;
    // An immutable copy of the final plan
    private ImmutableList<Node> scheduledNodes;

    public DefaultExecutionPlan(
        String displayName,
        TaskNodeFactory taskNodeFactory,
        OrdinalGroupFactory ordinalGroupFactory,
        TaskDependencyResolver dependencyResolver,
        ExecutionNodeAccessHierarchy outputHierarchy,
        ExecutionNodeAccessHierarchy destroyableHierarchy,
        ResourceLockCoordinationService lockCoordinator
    ) {
        this.displayName = displayName;
        this.taskNodeFactory = taskNodeFactory;
        this.dependencyResolver = dependencyResolver;
        this.outputHierarchy = outputHierarchy;
        this.destroyableHierarchy = destroyableHierarchy;
        this.lockCoordinator = lockCoordinator;
        this.ordinalNodeAccess = new OrdinalNodeAccess(ordinalGroupFactory);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public QueryableExecutionPlan getContents() {
        return this;
    }

    @Override
    public TaskNode getNode(Task task) {
        return nodeMapping.get(task);
    }

    @Override
    public void setScheduledNodes(Collection<? extends Node> nodes) {
        if (scheduledNodes != null) {
            throw new IllegalStateException("This execution plan already has nodes scheduled.");
        }
        entryNodes.addAll(nodes);
        scheduledNodes = ImmutableList.copyOf(nodes);
        nodeMapping.addAll(nodes);
    }

    @Override
    public void addEntryTask(Task task) {
        addEntryTasks(Collections.singletonList(task));
    }

    @Override
    public void addEntryTasks(Collection<? extends Task> tasks) {
        addEntryTasks(tasks, order++);
    }

    private void addEntryTasks(Collection<? extends Task> tasks, int ordinal) {
        SortedSet<Node> nodes = new TreeSet<>(NodeComparator.INSTANCE);
        for (Task task : tasks) {
            nodes.add(taskNodeFactory.getOrCreateNode(task));
        }
        doAddEntryNodes(nodes, ordinal);
    }

    public void addEntryNodes(Collection<? extends Node> nodes) {
        addEntryNodes(nodes, order++);
    }

    public void addEntryNodes(Collection<? extends Node> nodes, int ordinal) {
        SortedSet<Node> sorted = new TreeSet<>(NodeComparator.INSTANCE);
        sorted.addAll(nodes);
        doAddEntryNodes(sorted, ordinal);
    }

    private void doAddEntryNodes(SortedSet<? extends Node> nodes, int ordinal) {
        scheduledNodes = null;
        LinkedList<Node> queue = new LinkedList<>();
        OrdinalGroup group = ordinalNodeAccess.group(ordinal);

        for (Node node : nodes) {
            node.maybeInheritOrdinalAsDependency(group);
            group.addEntryNode(node);
            entryNodes.add(node);
            queue.add(node);
        }

        discoverNodeRelationships(queue);
    }

    private void discoverNodeRelationships(LinkedList<Node> queue) {
        Set<Node> visiting = new HashSet<>();
        while (!queue.isEmpty()) {
            Node node = queue.getFirst();
            node.prepareForScheduling();
            if (node.getDependenciesProcessed() || node.isCannotRunInAnyPlan()) {
                // Have already visited this node or have already executed it - skip it
                queue.removeFirst();
                continue;
            }

            boolean filtered = !nodeSatisfiesTaskFilter(node);
            if (filtered) {
                // Task is not required - skip it
                queue.removeFirst();
                node.dependenciesProcessed();
                node.filtered();
                filteredNodes.add(node);
                continue;
            }
            node.require();

            if (visiting.add(node)) {
                // Have not seen this node before - add its dependencies to the head of the queue and leave this
                // node in the queue
                node.resolveDependencies(dependencyResolver);
                for (Node successor : node.getHardSuccessors()) {
                    successor.maybeInheritOrdinalAsDependency(node.getGroup().asOrdinal());
                }
                ListIterator<Node> insertPoint = queue.listIterator();
                for (Node successor : node.getDependencySuccessors()) {
                    if (!visiting.contains(successor)) {
                        insertPoint.add(successor);
                    }
                }
            } else {
                // Have visited this node's dependencies - add it to the graph
                queue.removeFirst();
                visiting.remove(node);
                node.dependenciesProcessed();
                // Finalizers run immediately after the node
                for (Node finalizer : node.getFinalizers()) {
                    finalizers.add(finalizer);
                    if (!visiting.contains(finalizer)) {
                        queue.addFirst(finalizer);
                    }
                }
            }
        }
    }

    private boolean nodeSatisfiesTaskFilter(Node successor) {
        if (successor instanceof LocalTaskNode) {
            return filter.isSatisfiedBy(((LocalTaskNode) successor).getTask());
        }
        return true;
    }

    @Override
    public void determineExecutionPlan() {
        if (scheduledNodes == null) {
            scheduledNodes = new DetermineExecutionPlanAction(
                nodeMapping,
                ordinalNodeAccess,
                entryNodes,
                finalizers
            ).run();
            finalizers.clear();
        }
    }

    @Override
    public FinalizedExecutionPlan finalizePlan() {
        if (scheduledNodes == null) {
            throw new IllegalStateException("Nodes have node been scheduled yet.");
        }
        if (finalizedPlan == null) {
            dependencyResolver.clear();
            // Should make an immutable copy of the contents to pass to the finalized plan and also to use in this instance
            finalizedPlan = new DefaultFinalizedExecutionPlan(displayName, ordinalNodeAccess, outputHierarchy, destroyableHierarchy, lockCoordinator, scheduledNodes, continueOnFailure, this, completionHandler);
        }
        return finalizedPlan;
    }

    @Override
    public void close() {
        if (finalizedPlan != null) {
            finalizedPlan.close();
        }
        for (Node node : nodeMapping) {
            node.reset();
        }
        for (Node node : filteredNodes) {
            node.reset();
        }
        completionHandler = localTaskNode -> {
        };
        entryNodes.clear();
        nodeMapping.clear();
        filteredNodes.clear();
        finalizers.clear();
        scheduledNodes = null;
        ordinalNodeAccess.reset();
        dependencyResolver.clear();
    }

    @Override
    public void onComplete(Consumer<LocalTaskNode> handler) {
        Consumer<LocalTaskNode> previous = this.completionHandler;
        this.completionHandler = node -> {
            previous.accept(node);
            handler.accept(node);
        };
    }

    @Override
    public Set<Task> getTasks() {
        return nodeMapping.getTasks();
    }

    @Override
    public Set<Task> getRequestedTasks() {
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (Node entryNode : entryNodes) {
            if (entryNode instanceof LocalTaskNode) {
                builder.add(((LocalTaskNode) entryNode).getTask());
            }
        }
        return builder.build();
    }

    @Override
    public ScheduledNodes getScheduledNodes() {
        if (scheduledNodes == null) {
            throw new IllegalStateException("Nodes have node been scheduled yet.");
        }
        for (Node node : scheduledNodes) {
            if (node instanceof TaskNode) {
                // The task for a node can be attached lazily
                // Ensure the task is available if the caller happens to need it.
                // It would be better for callers to not touch nodes directly, but instead take some immutable snapshot here
                ((TaskNode) node).getTask();
            }
        }
        return new ScheduledNodes() {
            // Hold a reference to the plan, as the field is discarded when the plan completes
            final ImmutableList<Node> plan = scheduledNodes;

            @Override
            public void visitNodes(Consumer<List<Node>> visitor) {
                visitor.accept(plan);
            }
        };
    }

    @Override
    public Set<Task> getFilteredTasks() {
        ImmutableSet.Builder<Task> builder = ImmutableSet.builder();
        for (Node filteredNode : filteredNodes) {
            if (filteredNode instanceof LocalTaskNode) {
                builder.add(((LocalTaskNode) filteredNode).getTask());
            }
        }
        return builder.build();
    }

    @Override
    public void addFilter(Spec<? super Task> filter) {
        this.filter = Specs.intersect(this.filter, filter);
    }

    @Override
    public void setContinueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
    }

    @Override
    public int size() {
        return nodeMapping.getNumberOfPublicNodes();
    }

    static class NodeMapping extends AbstractCollection<Node> {
        private final Map<Task, LocalTaskNode> taskMapping = Maps.newLinkedHashMap();
        private final Set<Node> nodes = Sets.newLinkedHashSet();

        @Override
        public boolean contains(Object o) {
            return nodes.contains(o);
        }

        @Override
        public boolean add(Node node) {
            if (!nodes.add(node)) {
                return false;
            }
            if (node instanceof LocalTaskNode) {
                LocalTaskNode taskNode = (LocalTaskNode) node;
                taskMapping.put(taskNode.getTask(), taskNode);
            }
            return true;
        }

        public LocalTaskNode get(Task task) {
            LocalTaskNode taskNode = taskMapping.get(task);
            if (taskNode == null) {
                throw new IllegalStateException("Task is not part of the execution plan, no dependency information is available.");
            }
            return taskNode;
        }

        public Set<Task> getTasks() {
            return taskMapping.keySet();
        }

        @Override
        public Iterator<Node> iterator() {
            return nodes.iterator();
        }

        @Override
        public void clear() {
            nodes.clear();
            taskMapping.clear();
        }

        @Override
        public int size() {
            return nodes.size();
        }

        public int getNumberOfPublicNodes() {
            int publicNodes = 0;
            for (Node node : this) {
                if (node.isPublicNode()) {
                    publicNodes++;
                }
            }
            return publicNodes;
        }

        public void retainFirst(int count) {
            Iterator<Node> executionPlanIterator = nodes.iterator();
            for (int i = 0; i < count; i++) {
                executionPlanIterator.next();
            }
            while (executionPlanIterator.hasNext()) {
                Node removedNode = executionPlanIterator.next();
                executionPlanIterator.remove();
                if (removedNode instanceof LocalTaskNode) {
                    taskMapping.remove(((LocalTaskNode) removedNode).getTask());
                }
            }
        }
    }
}
