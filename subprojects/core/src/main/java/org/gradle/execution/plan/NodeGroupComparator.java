/*
 * Copyright 2024 the original author or authors.
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

import javax.annotation.Nonnull;
import java.util.Comparator;

public class NodeGroupComparator implements Comparator<NodeGroup> {
    @Nonnull
    public static final NodeGroupComparator INSTANCE = new NodeGroupComparator();

    @Override
    public int compare(NodeGroup o1, NodeGroup o2) {
        if (o1 == o2) {
            return 0;
        }
        if (o1 == NodeGroup.DEFAULT_GROUP  || o2 == NodeGroup.DEFAULT_GROUP) {
            return o1 == NodeGroup.DEFAULT_GROUP ? -1 : 1;
        }
        if (o1 instanceof OrdinalGroup || o2 instanceof OrdinalGroup) {
            if (o1 instanceof OrdinalGroup && o2 instanceof OrdinalGroup) {
                return ((OrdinalGroup) o1).getOrdinal() - ((OrdinalGroup) o2).getOrdinal();
            }
            return o1 instanceof OrdinalGroup ? -1 : 1;
        }
        if (o1 instanceof FinalizerGroup || o2 instanceof FinalizerGroup) {
            if (o1 instanceof FinalizerGroup && o2 instanceof FinalizerGroup) {
                return NodeComparator.INSTANCE.compare(((FinalizerGroup) o1).getNode(), ((FinalizerGroup) o2).getNode());
            }
            return o1 instanceof FinalizerGroup ? -1 : 1;
        }
        return System.identityHashCode(o1) - System.identityHashCode(o2);
    }
}
