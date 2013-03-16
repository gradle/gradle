/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.graph;

import org.gradle.api.Action;
import org.gradle.logging.StyledTextOutput;

import static org.gradle.logging.StyledTextOutput.Style.Info;

public class GraphRenderer {
    private final StyledTextOutput output;
    private StringBuilder prefix = new StringBuilder();
    private boolean seenRootChildren;
    private boolean lastChild = true;

    public GraphRenderer(StyledTextOutput output) {
        this.output = output;
    }

    /**
     * Visits a node in the graph.
     */
    public void visit(Action<? super StyledTextOutput> node, boolean lastChild) {
        if (seenRootChildren) {
            output.withStyle(Info).text(prefix + (lastChild ? "\\--- " : "+--- "));
        }
        this.lastChild = lastChild;
        node.execute(output);
        output.println();
    }

    /**
     * Starts visiting the children of the most recently visited node.
     */
    public void startChildren() {
        if (seenRootChildren) {
            prefix.append(lastChild ? "     " : "|    ");
        }
        seenRootChildren = true;
    }

    /**
     * Completes visiting the children of the node which most recently started visiting children.
     */
    public void completeChildren() {
        if (prefix.length() == 0) {
            seenRootChildren = false;
        } else {
            prefix.setLength(prefix.length() - 5);
        }
    }

    public StyledTextOutput getOutput() {
        return output;
    }
}
