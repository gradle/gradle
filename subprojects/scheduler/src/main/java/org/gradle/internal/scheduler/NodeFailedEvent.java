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

import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF;
import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.DEPENDENCY_FAILED;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;
import static org.gradle.internal.scheduler.NodeState.SHOULD_RUN;

public class NodeFailedEvent extends AbstractNodeCompletionEvent {
    private final Throwable failure;

    public NodeFailedEvent(Node node, Throwable failure) {
        super(node);
        this.failure = failure;
    }

    @Override
    protected void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures) {
        failures.add(failure);
        graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
            @Override
            public void execute(Edge outgoing) {
                Node target = outgoing.getTarget();
                // TODO Handle remaining incoming edges when suspended node is skipped
                if (outgoing.getType() == DEPENDENCY_OF) {
                    target.setState(DEPENDENCY_FAILED);
                }
            }
        });

        // Cancel all runnable nodes (including any that is still running) if `--continue` is off
        if (!continueOnFailure) {
            System.out.println("Marking all runnable nodes as cancelled because of failure");
            for (Node candidate : graph.getAllNodes()) {
                if (candidate.getState() == RUNNABLE || candidate.getState() == SHOULD_RUN) {
                    System.out.printf("Marking %s as cancelled%n", candidate);
                    candidate.setState(CANCELLED);
                }
            }
        }
    }

    @Override
    public String toString() {
        return String.format("FAILED %s (%s)", node, failure.getClass().getSimpleName());
    }
}
