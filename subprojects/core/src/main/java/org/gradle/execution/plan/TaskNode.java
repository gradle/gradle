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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NavigableSet;
import java.util.Set;

public abstract class TaskNode extends Node {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskNode.class);

    private final NavigableSet<Node> mustSuccessors = Sets.newTreeSet();
    private final Set<Node> mustPredecessors = Sets.newHashSet();
    private final NavigableSet<Node> shouldSuccessors = Sets.newTreeSet();
    private final NavigableSet<Node> finalizers = Sets.newTreeSet();
    private final NavigableSet<Node> finalizingSuccessors = Sets.newTreeSet();

    @Override
    public boolean doCheckDependenciesComplete() {
        if (!super.doCheckDependenciesComplete()) {
            return false;
        }
        LOGGER.debug("Checking if all must successors are complete for {}", this);
        for (Node dependency : mustSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        LOGGER.debug("Checking if all finalizing successors are complete for {}", this);
        for (Node dependency : finalizingSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        LOGGER.debug("All task dependencies are complete for {}", this);
        return true;
    }

    public Set<Node> getMustSuccessors() {
        return mustSuccessors;
    }

    @Override
    public Set<Node> getFinalizers() {
        return finalizers;
    }

    public Set<Node> getFinalizingSuccessors() {
        return finalizingSuccessors;
    }

    public Set<Node> getShouldSuccessors() {
        return shouldSuccessors;
    }

    public void addMustSuccessor(TaskNode toNode) {
        mustSuccessors.add(toNode);
        toNode.mustPredecessors.add(this);
    }

    public void addFinalizingSuccessor(TaskNode finalized) {
        finalizingSuccessors.add(finalized);
        finalized.finalizers.add(this);
    }

    public void addFinalizer(TaskNode finalizerNode) {
        finalizerNode.addFinalizingSuccessor(this);
    }

    public void addShouldSuccessor(Node toNode) {
        shouldSuccessors.add(toNode);
    }

    public void removeShouldSuccessor(TaskNode toNode) {
        shouldSuccessors.remove(toNode);
    }

    @Override
    public Iterable<Node> getAllSuccessors() {
        return Iterables.concat(
            shouldSuccessors,
            finalizingSuccessors,
            mustSuccessors,
            super.getAllSuccessors()
        );
    }

    @Override
    public Iterable<Node> getAllSuccessorsInReverseOrder() {
        return Iterables.concat(
            super.getAllSuccessorsInReverseOrder(),
            mustSuccessors.descendingSet(),
            finalizingSuccessors.descendingSet(),
            shouldSuccessors.descendingSet()
        );
    }

    @Override
    public Iterable<Node> getAllPredecessors() {
        return Iterables.concat(mustPredecessors, finalizers, super.getAllPredecessors());
    }

    @Override
    public boolean hasHardSuccessor(Node successor) {
        if (super.hasHardSuccessor(successor)) {
            return true;
        }
        if (!(successor instanceof TaskNode)) {
            return false;
        }
        return getMustSuccessors().contains(successor)
            || getFinalizingSuccessors().contains(successor);
    }

    /**
     * Attach an action to execute immediately after the <em>successful</em> completion of this task.
     *
     * <p>This is used to ensure that dependency resolution metadata for a particular artifact is calculated immediately after that artifact is produced and cached, to avoid consuming tasks having to lock the producing project in order to calculate this metadata.</p>
     *
     * <p>This action should really be modelled as a real node in the graph. This 'post action' concept is intended to be a step in this direction.</p>
     */
    public abstract void appendPostAction(Action<? super Task> action);

    public abstract Action<? super Task> getPostAction();

    public abstract TaskInternal getTask();

    @Override
    public boolean isPublicNode() {
        return true;
    }
}
