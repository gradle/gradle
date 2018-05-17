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

public abstract class AbstractNodeCompletionEvent extends Event {
    protected AbstractNodeCompletionEvent(Node node) {
        super(node);
    }

    @Override
    public void handle(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
        switch (node.getState()) {
            case RUNNABLE:
            case SHOULD_RUN:
            case MUST_RUN:
                executedNodes.add(node);
                break;
            default:
                break;
        }
        super.handle(graph, continueOnFailure, executedNodes, failures);
    }
}
