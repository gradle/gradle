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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;

import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.DEPENDENCY_FAILED;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;

public class NodeFinishedEvent extends AbstractNodeCompletionEvent {
    public NodeFinishedEvent(Node node) {
        super(node);
    }

    @Override
    protected void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures) {
        final NodeState finishedNodeState = node.getState();
        graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
            @Override
            public void execute(Edge outgoing) {
                Node target = outgoing.getTarget();
                switch (finishedNodeState) {
                    case RUNNABLE:
                    case SHOULD_RUN:
                    case MUST_RUN:
                        if (target.getState() == CANCELLED) {
                            target.setState(RUNNABLE);
                        }
                        break;
                    case CANCELLED:
                        // TODO Handle remaining incoming edges when suspended node is skipped
                        if (target.getState() == RUNNABLE) {
                            target.setState(CANCELLED);
                        }
                        break;
                    case DEPENDENCY_FAILED:
                        // TODO Handle remaining incoming edges when suspended node is skipped
                        switch (outgoing.getType()) {
                            case DEPENDENCY_OF:
                                target.setState(DEPENDENCY_FAILED);
                                break;
                            case FINALIZED_BY:
                            case AVOID_STARTING_BEFORE_FINALIZED:
                                if (target.getState() == RUNNABLE) {
                                    target.setState(CANCELLED);
                                }
                        }
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        });
    }

    @Override
    public String toString() {
        return String.format("FINISHED %s (%s)", node, node.getState());
    }
}
