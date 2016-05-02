/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StreamingStyledTextOutput;

import java.util.*;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class DirectedGraphRenderer<N> {
    private final GraphNodeRenderer<N> nodeRenderer;
    private final DirectedGraph<N, ?> graph;
    private boolean omittedDetails;

    public DirectedGraphRenderer(GraphNodeRenderer<N> nodeRenderer, DirectedGraph<N, ?> graph) {
        this.nodeRenderer = nodeRenderer;
        this.graph = graph;
    }

    public void renderTo(N root, Appendable output) {
        renderTo(root, new StreamingStyledTextOutput(output));
    }

    public void renderTo(N root, StyledTextOutput output) {
        GraphRenderer renderer = new GraphRenderer(output);
        Set<N> rendered = new HashSet<N>();
        omittedDetails = false;
        renderTo(root, renderer, rendered, false);
        if (omittedDetails) {
            output.println();
            output.withStyle(Info).println("(*) - details omitted (listed previously)");
        }
    }

    private void renderTo(final N node, GraphRenderer graphRenderer, Collection<N> rendered, boolean lastChild) {
        final boolean alreadySeen = !rendered.add(node);

        graphRenderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput output) {
                nodeRenderer.renderTo(node, output);
                if (alreadySeen) {
                    output.text(" (*)");
                }
            }
        }, lastChild);

        if (alreadySeen) {
            omittedDetails = true;
            return;
        }

        List<N> children = new ArrayList<N>();
        graph.getNodeValues(node, new HashSet<Object>(), children);
        if (children.isEmpty()) {
            return;
        }
        graphRenderer.startChildren();
        for (int i = 0; i < children.size(); i++) {
            N child = children.get(i);
            renderTo(child, graphRenderer, rendered, i == children.size() - 1);
        }
        graphRenderer.completeChildren();
    }
}
