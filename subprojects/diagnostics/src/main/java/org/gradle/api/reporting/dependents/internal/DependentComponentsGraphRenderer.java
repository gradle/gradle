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

package org.gradle.api.reporting.dependents.internal;

import org.gradle.api.Action;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class DependentComponentsGraphRenderer {

    private final GraphRenderer renderer;
    private final DependentBinaryNodeRenderer nodeRenderer;

    public DependentComponentsGraphRenderer(GraphRenderer renderer) {
        this.renderer = renderer;
        this.nodeRenderer = new DependentBinaryNodeRenderer();
    }

    public void render(DependentComponentsRenderableDependency root) {
        renderChildren(root.getChildren());
    }

    private void renderChildren(Set<? extends RenderableDependency> children) {
        renderer.startChildren();
        int idx = 0;
        for (RenderableDependency child : children) {
            boolean last = idx++ == children.size() - 1;
            doRender(child, last);
        }
        renderer.completeChildren();
    }

    private void doRender(final RenderableDependency node, boolean last) {
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput output) {
                nodeRenderer.renderNode(output, node, false);
            }
        }, last);
        renderChildren(node.getChildren());
    }

    public boolean hasSeenTestSuite() {
        return nodeRenderer.seenTestSuite;
    }

    private static class DependentBinaryNodeRenderer implements NodeRenderer {

        private boolean seenTestSuite;

        @Override
        public void renderNode(StyledTextOutput output, RenderableDependency node, boolean alreadyRendered) {
            output.text(node.getName());
            if (node instanceof DependentComponentsRenderableDependency) {
                DependentComponentsRenderableDependency dep = (DependentComponentsRenderableDependency) node;
                if (dep.isTestSuite()) {
                    output.withStyle(Info).text(" (t)");
                    seenTestSuite = true;
                }
                if (!dep.isBuildable()) {
                    output.withStyle(Info).text(" NOT BUILDABLE");
                }
            }
        }
    }
}
