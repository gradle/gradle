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

package org.gradle.execution.taskgraph;

import com.google.common.collect.Iterables;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.TreeSet;

public abstract class TaskInfo extends WorkInfo {

    private boolean dependenciesProcessed;
    private final TreeSet<TaskInfo> mustSuccessors = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> shouldSuccessors = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> finalizers = new TreeSet<TaskInfo>();
    private final TreeSet<TaskInfo> finalizingSuccessors = new TreeSet<TaskInfo>();

    public TaskInfo() {
        super();
    }

    @Override
    public abstract TaskInternal getWork();

    @Override
    public void rethrowFailure() {
        getWork().getState().rethrowFailure();
    }

    public abstract boolean satisfies(Spec<? super Task> filter);

    public abstract void prepareForExecution();

    public abstract Collection<? extends TaskInfo> getDependencies(TaskDependencyResolver dependencyResolver);

    public abstract Collection<? extends TaskInfo> getFinalizedBy(TaskDependencyResolver dependencyResolver);

    public abstract Collection<? extends TaskInfo> getMustRunAfter(TaskDependencyResolver dependencyResolver);

    public abstract Collection<? extends TaskInfo> getShouldRunAfter(TaskDependencyResolver dependencyResolver);

    @Override
    public boolean allDependenciesComplete() {
        if (!super.allDependenciesComplete()) {
            return false;
        }
        for (TaskInfo dependency : mustSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        for (TaskInfo dependency : finalizingSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        return true;
    }

    public TreeSet<TaskInfo> getMustSuccessors() {
        return mustSuccessors;
    }

    public TreeSet<TaskInfo> getFinalizers() {
        return finalizers;
    }

    public TreeSet<TaskInfo> getFinalizingSuccessors() {
        return finalizingSuccessors;
    }

    public TreeSet<TaskInfo> getShouldSuccessors() {
        return shouldSuccessors;
    }

    public boolean getDependenciesProcessed() {
        return dependenciesProcessed;
    }

    public void dependenciesProcessed() {
        dependenciesProcessed = true;
    }

    public void addMustSuccessor(TaskInfo toNode) {
        mustSuccessors.add(toNode);
    }

    public void addFinalizingSuccessor(TaskInfo finalized) {
        finalizingSuccessors.add(finalized);
    }

    public void addFinalizer(TaskInfo finalizerNode) {
        finalizers.add(finalizerNode);
        finalizerNode.addFinalizingSuccessor(this);
    }

    public void addShouldSuccessor(TaskInfo toNode) {
        shouldSuccessors.add(toNode);
    }

    public void removeShouldRunAfterSuccessor(TaskInfo toNode) {
        shouldSuccessors.remove(toNode);
    }

    @Override
    public Iterable<WorkInfo> getAllSuccessors() {
        return Iterables.concat(getMustSuccessors(), getFinalizingSuccessors(), super.getAllSuccessors());
    }
    @Override
    public Iterable<WorkInfo> getAllSuccessorsInReverseOrder() {
        return Iterables.concat(
            super.getAllSuccessorsInReverseOrder(),
            mustSuccessors.descendingSet(),
            finalizingSuccessors.descendingSet(),
            shouldSuccessors.descendingSet()
        );
    }

    @Override
    public boolean hasSuccessor(WorkInfo successor) {
        if (super.hasSuccessor(successor)) {
            return true;
        }
        if (!(successor instanceof TaskInfo)) {
            return false;
        }
        return getMustSuccessors().contains(successor)
            || getFinalizingSuccessors().contains(successor);
    }

    abstract public String toString();
}
