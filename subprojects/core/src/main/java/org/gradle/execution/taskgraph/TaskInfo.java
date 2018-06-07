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
import com.google.common.collect.Sets;

import java.util.NavigableSet;
import java.util.Set;

public abstract class TaskInfo extends WorkInfo {

    private final NavigableSet<WorkInfo> mustSuccessors = Sets.newTreeSet();
    private final NavigableSet<WorkInfo> shouldSuccessors = Sets.newTreeSet();
    private final NavigableSet<WorkInfo> finalizers = Sets.newTreeSet();
    private final NavigableSet<WorkInfo> finalizingSuccessors = Sets.newTreeSet();

    @Override
    public boolean allDependenciesComplete() {
        if (!super.allDependenciesComplete()) {
            return false;
        }
        for (WorkInfo dependency : mustSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        for (WorkInfo dependency : finalizingSuccessors) {
            if (!dependency.isComplete()) {
                return false;
            }
        }

        return true;
    }

    public Set<WorkInfo> getMustSuccessors() {
        return mustSuccessors;
    }

    public Set<WorkInfo> getFinalizers() {
        return finalizers;
    }

    public Set<WorkInfo> getFinalizingSuccessors() {
        return finalizingSuccessors;
    }

    public Set<WorkInfo> getShouldSuccessors() {
        return shouldSuccessors;
    }

    protected void addMustSuccessor(WorkInfo toNode) {
        mustSuccessors.add(toNode);
    }

    protected void addFinalizingSuccessor(TaskInfo finalized) {
        finalizingSuccessors.add(finalized);
    }

    protected void addFinalizer(TaskInfo finalizerNode) {
        finalizers.add(finalizerNode);
        finalizerNode.addFinalizingSuccessor(this);
    }

    protected void addShouldSuccessor(WorkInfo toNode) {
        shouldSuccessors.add(toNode);
    }

    public void removeShouldSuccessor(TaskInfo toNode) {
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
    public boolean hasHardSuccessor(WorkInfo successor) {
        if (super.hasHardSuccessor(successor)) {
            return true;
        }
        if (!(successor instanceof TaskInfo)) {
            return false;
        }
        return getMustSuccessors().contains(successor)
            || getFinalizingSuccessors().contains(successor);
    }
}
