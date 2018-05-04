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

import org.gradle.api.Action;

import java.util.Set;

import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.FAILED;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;

public class NodeFinishedEvent implements Event {
    private final Node node;

    public NodeFinishedEvent(Node node) {
        this.node = node;
    }

    @Override
    public void handle(Graph graph, Set<Node> runningNodes) {
        runningNodes.remove(node);
        switch (node.getState()) {
            case RUNNABLE:
            case MUST_RUN:
                graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                    @Override
                    public void execute(Edge edge) {
                        Node target = edge.getTarget();
                        if (target.getState() == CANCELLED) {
                            target.setState(RUNNABLE);
                        }
                    }
                });
                break;
            case CANCELLED:
                // TODO Handle remaining incoming edges when suspended node is skipped
                graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                    @Override
                    public void execute(Edge outgoing) {
                        Node target = outgoing.getTarget();
                        if (target.getState() == RUNNABLE) {
                            target.setState(CANCELLED);
                        }
                    }
                });
                break;
            case FAILED:
                // TODO Cancel everything if `--continue` is not enabled
                graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                    @Override
                    public void execute(Edge outgoing) {
                        if (outgoing.getType() == EdgeType.DEPENDENT) {
                            outgoing.getTarget().setState(FAILED);
                        }
                    }
                });
                break;
        }
    }

    @Override
    public String toString() {
        return String.format("FINISHED %s (%s)", node, node.getState());
    }
}
