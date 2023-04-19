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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sorts {@link Node}s to execute in the following order:
 * <ol>
 *    <li>{@link OrdinalNode} and {@link ResolveMutationsNode}</li>
 *    <li>{@link CreationOrderedNode} (a.k.a. transform nodes)</li>
 *    <li>{@link LocalTaskNode}</li>
 *    <li>{@link ActionNode}</li>
 *    <li>{@link TaskInAnotherBuild}</li>
 *    <li>remaining nodes are ordered by class name</li>
 * </ol>
 */
public class NodeComparator implements Comparator<Node> {

    public static final NodeComparator INSTANCE = new NodeComparator();

    public final Set<Long> precedence = Collections.synchronizedSet(new LinkedHashSet<>());

    private NodeComparator() {
    }

    @Override
    public int compare(Node o1, Node o2) {

        if (o1 instanceof OrdinalNode || o1 instanceof ResolveMutationsNode) {
            if (o1.equals(o2)) {
                return 0;
            } else {
                return -1;
            }
        }
        if (o2 instanceof OrdinalNode || o2 instanceof ResolveMutationsNode) {
            return 1;
        }

        if (o1 instanceof CreationOrderedNode) {
            if (o2 instanceof CreationOrderedNode) {
                return ((CreationOrderedNode) o1).getOrder() - ((CreationOrderedNode) o2).getOrder();
            }
            return -1;
        }
        if (o2 instanceof CreationOrderedNode) {
            return 1;
        }

        if (o1 instanceof LocalTaskNode) {
            if (o2 instanceof LocalTaskNode) {
                return compareLocalTaskNodes((LocalTaskNode) o1, (LocalTaskNode) o2);
            }
            return -1;
        }
        if (o2 instanceof LocalTaskNode) {
            return 1;
        }

        if (o1 instanceof ActionNode) {
            return -1;
        }
        if (o2 instanceof ActionNode) {
            return 1;
        }

        if (o1 instanceof TaskInAnotherBuild && o2 instanceof TaskInAnotherBuild) {
            return ((TaskInAnotherBuild) o1).getTaskIdentityPath().compareTo(
                ((TaskInAnotherBuild) o2).getTaskIdentityPath()
            );
        }
        int diff = o1.getClass().getName().compareTo(o2.getClass().getName());
        if (diff != 0) {
            return diff;
        }
        return -1;
    }

    private int compareLocalTaskNodes(LocalTaskNode o1, LocalTaskNode o2) {
        if (o1.getTask() == o2.getTask()) {
            return 0;
        }
        int o1Identity = System.identityHashCode(o1.getTask());
        int o2Identity = System.identityHashCode(o2.getTask());
        if (precedence.contains(toLong(o1Identity, o2Identity))) {
            return -1;
        }
        if (precedence.contains(toLong(o2Identity, o1Identity))) {
            return 1;
        }
        int computed = o1.getTask().compareTo(
            o2.getTask()
        );
        if (computed < 0) {
            precedence.add(toLong(o1Identity, o2Identity));
        } else if (computed > 0) {
            precedence.add(toLong(o2Identity, o1Identity));
        }
        return computed;
    }

    private static Long toLong(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
