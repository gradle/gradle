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

import java.util.Set;

abstract class HasFinalizers extends NodeGroup {
    public abstract NodeGroup getOrdinalGroup();

    public abstract Set<FinalizerGroup> getFinalizerGroups();

    /**
     * Tries to add the node to the set of exclusive nodes of this group. If this group doesn't support
     * exclusive nodes, e.g. it is a CompositeNodeGroup, then does nothing.
     *
     * The node should be an exclusive node of a single {@link FinalizerGroup} (though it can be non-exclusive member of multiple groups still),
     * see that class' documentation for details.
     *
     * @param node the node to add to the set of exclusive nodes
     */
    public abstract void maybeAddToOwnedMembers(Node node);

    /**
     * Removes the node from the list of owned nodes of a finalizer group.
     * Unlike {@link #maybeAddToOwnedMembers(Node)}, the removal affects all finalizer groups
     * returned by {@link #getFinalizerGroups()}.
     *
     * The node should be an exclusive node of a single {@link FinalizerGroup} (though it can be non-exclusive member of multiple groups still),
     * see that class' documentation for details.
     *
     * @param node the node
     */
    public abstract void removeFromOwnedMembers(Node node);
}
