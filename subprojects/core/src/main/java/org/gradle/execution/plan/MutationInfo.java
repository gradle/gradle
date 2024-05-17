/*
 * Copyright 2019 the original author or authors.
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

class MutationInfo {
    private final Set<Node> nodesYetToConsumeOutput = new HashSet<>();
    final Set<String> outputPaths = new HashSet<>();
    final Set<String> destroyablePaths = new HashSet<>();
    boolean hasFileInputs;
    boolean hasOutputs;
    boolean hasLocalState;
    boolean hasValidationProblem;
    private boolean outputProduced;

    void started() {
        outputProduced = true;
    }

    boolean isOutputProducedButNotYetConsumed() {
        return outputProduced && !nodesYetToConsumeOutput.isEmpty();
    }

    public Set<Node> getNodesYetToConsumeOutput() {
        return nodesYetToConsumeOutput;
    }

    public void consumerCompleted(Node node) {
        nodesYetToConsumeOutput.remove(node);
    }

    public void addConsumer(Node node) {
        nodesYetToConsumeOutput.add(node);
    }
}
