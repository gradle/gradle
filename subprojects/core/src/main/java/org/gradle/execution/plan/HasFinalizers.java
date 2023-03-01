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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class HasFinalizers extends NodeGroup {
    public abstract NodeGroup getOrdinalGroup();

    public abstract Set<FinalizerGroup> getFinalizerGroups();

    protected boolean isCanCancel(Collection<FinalizerGroup> groups) {
        // A node cannot be cancelled if it belongs to a finalizer group that contains a finalized node that has started execution or that cannot be cancelled, and whose finalizer
        // can potentially still execute
        // So visit all the finalizer groups reachable from groups that the node belongs to and the finalized nodes of those groups
        Set<FinalizerGroup> seen = new HashSet<>();
        List<FinalizerGroup> queue = new ArrayList<>(groups);
        while (!queue.isEmpty()) {
            FinalizerGroup group = queue.remove(0);
            if (!group.isCanCancelSelf()) {
                // Has started running at least one finalized node, so cannot cancel
                return false;
            }
            if (seen.add(group)) {
                for (Node node : group.getFinalizedNodes()) {
                    if (node.getGroup() instanceof HasFinalizers) {
                        queue.addAll(((HasFinalizers) node.getGroup()).getFinalizerGroups());
                    }
                }
            }
            // Else, have already traversed this group
        }
        return true;
    }
}
