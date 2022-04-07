/*
 * Copyright 2022 the original author or authors.
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

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents a group of nodes that are reachable from more than one root node.
 */
public class CompositeNodeGroup extends HasFinalizers {
    private final NodeGroup ordinalGroup;
    private final Set<FinalizerGroup> finalizerGroups;
    private final boolean reachableFromEntryPoint;

    public CompositeNodeGroup(NodeGroup ordinalGroup, Set<FinalizerGroup> finalizerGroups) {
        this.ordinalGroup = ordinalGroup;
        this.finalizerGroups = finalizerGroups;
        this.reachableFromEntryPoint = reachableFromEntryPoint();
    }

    @Nullable
    @Override
    public OrdinalGroup asOrdinal() {
        return ordinalGroup.asOrdinal();
    }

    public NodeGroup getOrdinalGroup() {
        return ordinalGroup;
    }

    @Override
    public boolean isReachableFromEntryPoint() {
        return reachableFromEntryPoint;
    }

    private boolean reachableFromEntryPoint() {
        if (ordinalGroup.isReachableFromEntryPoint()) {
            return true;
        }
        for (FinalizerGroup group : finalizerGroups) {
            if (group.isReachableFromEntryPoint()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addMember(Node node) {
        for (FinalizerGroup group : finalizerGroups) {
            group.addMember(node);
        }
    }

    @Override
    public void removeMember(Node node) {
        for (FinalizerGroup group : finalizerGroups) {
            group.removeMember(node);
        }
    }

    @Override
    public Set<FinalizerGroup> getFinalizerGroups() {
        return finalizerGroups;
    }

    @Override
    public boolean isSuccessorsCompleteFor(Node node) {
        if (ordinalGroup.isReachableFromEntryPoint()) {
            // Reachable from entry point node, can run at any time
            return true;
        }
        boolean allComplete = true;
        for (FinalizerGroup group : finalizerGroups) {
            boolean complete = group.isSuccessorsCompleteFor(node);
            if (!complete) {
                allComplete = false;
            }
            // Can run once any of the finalizer groups is ready to run
            if (complete && group.isSuccessorsSuccessfulFor(node)) {
                return true;
            }
        }
        // No finalizer group is ready to run, and either all of them have failed or some are not yet complete
        return allComplete;
    }

    @Override
    public boolean isSuccessorsSuccessfulFor(Node node) {
        if (ordinalGroup.isReachableFromEntryPoint()) {
            // Reachable from entry point node, can run
            return true;
        }
        for (FinalizerGroup group : finalizerGroups) {
            if (group.isSuccessorsSuccessfulFor(node)) {
                return true;
            }
        }
        return false;
    }
}
