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

import java.util.Comparator;

/**
 * Sorts {@link Node}s to execute in the following order:
 * <ol>
 *    <li>{@link OrdinalNode}</li>
 *    <li>{@link ResolveMutationsNode}</li>
 *    <li>{@link CreationOrderedNode} (a.k.a. transform nodes)</li>
 *    <li>{@link LocalTaskNode}</li>
 *    <li>{@link ActionNode}</li>
 *    <li>{@link TaskInAnotherBuild}</li>
 * </ol>
 */
public class NodeComparator implements Comparator<Node> {

    public static final NodeComparator INSTANCE = new NodeComparator();

    private NodeComparator() {
    }

    @Override
    public int compare(Node n1, Node n2) {
        if (n1 == n2) {
            return 0;
        }

        if (n1 instanceof OrdinalNode) {
            if (n2 instanceof OrdinalNode) {
                OrdinalNode o1 = (OrdinalNode) n1;
                OrdinalNode o2 = (OrdinalNode) n2;
                int ordinalDiff = Integer.compare(
                    o1.getOrdinalGroup().getOrdinal(),
                    o2.getOrdinalGroup().getOrdinal()
                );
                if (ordinalDiff == 0) {
                    return o1.getType().compareTo(o2.getType());
                }
                return ordinalDiff;
            }
            return -1;
        }
        if (n2 instanceof OrdinalNode) {
            return 1;
        }

        if (n1 instanceof ResolveMutationsNode) {
            if (n2 instanceof ResolveMutationsNode) {
                return compareTaskNodes(
                    ((ResolveMutationsNode) n1).getNode(),
                    ((ResolveMutationsNode) n2).getNode()
                );
            }
            return -1;
        }
        if (n2 instanceof ResolveMutationsNode) {
            return 1;
        }

        if (n1 instanceof CreationOrderedNode) {
            if (n2 instanceof CreationOrderedNode) {
                return Integer.compare(
                    ((CreationOrderedNode) n1).getOrder(),
                    ((CreationOrderedNode) n2).getOrder()
                );
            }
            return -1;
        }
        if (n2 instanceof CreationOrderedNode) {
            return 1;
        }

        if (n1 instanceof LocalTaskNode) {
            if (n2 instanceof LocalTaskNode) {
                return compareTaskNodes((LocalTaskNode) n1, (LocalTaskNode) n2);
            }
            return -1;
        }
        if (n2 instanceof LocalTaskNode) {
            return 1;
        }

        if (n1 instanceof ActionNode) {
            if (n2 instanceof ActionNode) {
                return Integer.compare(
                    System.identityHashCode(n1),
                    System.identityHashCode(n2)
                );
            }
            return -1;
        }
        if (n2 instanceof ActionNode) {
            return 1;
        }

        if (n1 instanceof TaskInAnotherBuild && n2 instanceof TaskInAnotherBuild) {
            return ((TaskInAnotherBuild) n1).getTaskIdentityPath().compareTo(
                ((TaskInAnotherBuild) n2).getTaskIdentityPath()
            );
        }

        // For testing only.
        if (n1 instanceof ComparableNode && n2 instanceof ComparableNode) {
            return ((ComparableNode) n1).compareTo((ComparableNode) n2);
        }

        throw new IllegalArgumentException(String.format("Cannot compare nodes of type %s and %s", n1.getClass(), n2.getClass()));
    }

    /**
     * For testing only.
     */
    public static abstract class ComparableNode extends Node implements Comparable<ComparableNode> {}

    private static int compareTaskNodes(LocalTaskNode n1, LocalTaskNode n2) {
        return n1.getTask().compareTo(n2.getTask());
    }

}
