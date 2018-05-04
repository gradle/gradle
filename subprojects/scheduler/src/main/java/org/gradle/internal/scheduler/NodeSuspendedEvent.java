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

import java.util.Set;

import static org.gradle.internal.scheduler.EdgeType.MUST_NOT_RUN_WITH;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.KEEP;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.REMOVE;

public class NodeSuspendedEvent implements Event {
    private final Node node;

    public NodeSuspendedEvent(Node node) {
        this.node = node;
    }

    @Override
    public void handle(Graph graph, Set<Node> runningNodes) {
        runningNodes.remove(node);
        graph.processOutgoingEdges(node, new Graph.EdgeAction() {
            @Override
            public Graph.EdgeActionResult process(Edge edge) {
                return edge.getType() == MUST_NOT_RUN_WITH ? REMOVE : KEEP;
            }
        });
    }

    @Override
    public String toString() {
        return String.format("SUSPENDED %s (%s)", node, node.getState());
    }
}
