/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.graph

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.internal.graph.GraphRenderer
import org.gradle.logging.StyledTextOutput

import static org.gradle.logging.StyledTextOutput.Style.Info

class DependencyGraphRenderer {
    private final GraphRenderer renderer
    private final NodeRenderer nodeRenderer
    private boolean hasCyclicDependencies

    DependencyGraphRenderer(GraphRenderer renderer, NodeRenderer nodeRenderer) {
        this.renderer = renderer
        this.nodeRenderer = nodeRenderer
    }

    void render(RenderableDependency root) {
        def visited = new HashSet<ComponentIdentifier>()
        visited.add(root.getId())
        renderChildren(root.getChildren(), visited)
    }

    private void renderChildren(Set<? extends RenderableDependency> children, Set<ComponentIdentifier> visited) {
        renderer.startChildren()
        def i = 0
        for (RenderableDependency child : children) {
            boolean last = i++ == children.size() - 1
            doRender(child, last, visited)
        }
        renderer.completeChildren()
    }

    private void doRender(final RenderableDependency node, boolean last, Set<ComponentIdentifier> visited) {
        def children = node.getChildren()
        def alreadyRendered = !visited.add(node.getId())
        if (alreadyRendered) {
            hasCyclicDependencies = true
        }

        renderer.visit(new Action<StyledTextOutput>() {
            void execute(StyledTextOutput output) {
                nodeRenderer.renderNode(output, node, alreadyRendered)
            }
        }, last)

        if (!alreadyRendered) {
            renderChildren(children, visited)
        }
    }

    void printLegend() {
        if (hasCyclicDependencies) {
            renderer.output.println()
            renderer.output.withStyle(Info).println("(*) - dependencies omitted (listed previously)")
        }
    }
}
