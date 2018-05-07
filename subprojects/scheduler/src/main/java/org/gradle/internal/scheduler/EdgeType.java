/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scheduler;

public enum EdgeType {
    /**
     * Represents a dependency between two nodes, pointing from dependency node to dependent node.
     * The edge is removed when the source node is completed.
     */
    DEPENDENCY_OF,

    /**
     * The target node can only be started once the source node has fully finished running.
     * The edge is removed when the source node is completed.
     */
    MUST_COMPLETE_BEFORE,

    /**
     * The scheduler should try to avoid starting the target node before the source node is completed.
     * The edge can be removed to resolve cycles in the graph.
     * The edge is removed when the source node is completed.
     */
    SHOULD_COMPLETE_BEFORE,

    /**
     * The target node cannot be started while the source node is running.
     * The edge is removed when the source node is completed or suspended.
     */
    MUST_NOT_RUN_WITH,

    /**
     * The target node finalizes the source node.
     * Once the source node starts running, the target node is marked as
     * {@link NodeState#MUST_RUN}.
     * The edge is removed when the source node is completed.
     */
    FINALIZED_BY,

    /**
     * The target node is a dependency of one of the source node's finalizers.
     * The scheduler should try to avoid starting the target node before the source node is started.
     * The edge can be removed to resolve cycles in the graph.
     * The edge is removed when the source node starts executing.
     */
    AVOID_STARTING_BEFORE_FINALIZED
}
