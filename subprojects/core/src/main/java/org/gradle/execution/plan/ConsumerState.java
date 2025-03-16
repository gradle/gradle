/*
 * Copyright 2025 the original author or authors.
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

import java.util.HashSet;
import java.util.Set;

public final class ConsumerState {

    private boolean outputProduced = false;
    private final Set<Node> nodesYetToConsumeOutput = new HashSet<>();

    public void started() {
        outputProduced = true;
    }

    public boolean isOutputProducedButNotYetConsumed() {
        return outputProduced && !nodesYetToConsumeOutput.isEmpty();
    }

    public void addConsumer(Node node) {
        nodesYetToConsumeOutput.add(node);
    }

    public void consumerCompleted(Node node) {
        nodesYetToConsumeOutput.remove(node);
    }

    Set<Node> getNodesYetToConsumeOutput() {
        return nodesYetToConsumeOutput;
    }

}
