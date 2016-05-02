/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.graph;

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.HashSet;
import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class DependencyGraphRenderer {
    private final GraphRenderer renderer;
    private final NodeRenderer nodeRenderer;
    private boolean hasCyclicDependencies;

    public DependencyGraphRenderer(GraphRenderer renderer, NodeRenderer nodeRenderer) {
        this.renderer = renderer;
        this.nodeRenderer = nodeRenderer;
    }

    public void render(RenderableDependency root) {
        HashSet<Object> visited = Sets.newHashSet();
        visited.add(root.getId());
        renderChildren(root.getChildren(), visited);
    }

    private void renderChildren(Set<? extends RenderableDependency> children, Set<Object> visited) {
        renderer.startChildren();
        Integer i = 0;
        for (RenderableDependency child : children) {
            boolean last = i++ == children.size() - 1;
            doRender(child, last, visited);
        }

        renderer.completeChildren();
    }

    private void doRender(final RenderableDependency node, boolean last, Set<Object> visited) {
        Set<? extends RenderableDependency> children = node.getChildren();
        final boolean alreadyRendered = !visited.add(node.getId());
        if (alreadyRendered) {
            hasCyclicDependencies = true;
        }


        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput output) {
                nodeRenderer.renderNode(output, node, alreadyRendered);
            }

        }, last);

        if (!alreadyRendered) {
            renderChildren(children, visited);
        }

    }

    public void printLegend() {
        if (hasCyclicDependencies) {
            renderer.getOutput().println();
            renderer.getOutput().withStyle(Info).println("(*) - dependencies omitted (listed previously)");
        }

    }
}
