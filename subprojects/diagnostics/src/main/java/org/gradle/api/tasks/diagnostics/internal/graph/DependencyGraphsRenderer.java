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
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvableConfigurationResult;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is responsible for rendering multiple dependency graphs.
 * Each of them <i>may</i> have a specific root renderer (to display the dependency name, for example),
 * but it's not required. The graph renderer is used to render each of them.
 */
public class DependencyGraphsRenderer {
    private final StyledTextOutput output;
    private final GraphRenderer renderer;
    private final NodeRenderer rootRenderer;
    private final NodeRenderer dependenciesRenderer;
    private final LegendRenderer legendRenderer;
    private boolean showSinglePath;

    public DependencyGraphsRenderer(StyledTextOutput output, GraphRenderer renderer, NodeRenderer rootRenderer, NodeRenderer dependenciesRenderer) {
        this.output = output;
        this.renderer = renderer;
        this.rootRenderer = rootRenderer;
        this.dependenciesRenderer = dependenciesRenderer;
        this.legendRenderer = new LegendRenderer(output);
    }

    public boolean isShowSinglePath() {
        return showSinglePath;
    }

    public void setShowSinglePath(boolean showSinglePath) {
        this.showSinglePath = showSinglePath;
    }

    public void render(Collection<RenderableDependency> items) {
        int i = 0;
        int size = items.size();
        for (final RenderableDependency item : items) {
            renderRoot(item);
            boolean last = ++i == size;
            if (!last) {
                output.println();
            }
        }
    }

    private void renderRoot(final RenderableDependency root) {
        if (root instanceof UnresolvableConfigurationResult) {
            legendRenderer.setHasUnresolvableConfigurations(true);
        }
        if (rootRenderer != NodeRenderer.NO_OP) {
            renderNode(root, true, false, rootRenderer);
        }
        HashSet<Object> visited = Sets.newHashSet();
        visited.add(root.getId());
        renderChildren(root.getChildren(), visited);
    }

    private void renderChildren(Set<? extends RenderableDependency> children, Set<Object> visited) {
        int i = 0;
        int childCould = children.size();
        int count = showSinglePath ? Math.min(1, childCould) : childCould;
        if (count > 0) {
            renderer.startChildren();
            for (RenderableDependency child : children) {
                boolean last = ++i == count;
                doRender(child, last, visited);
                if (last) {
                    break;
                }
            }
            renderer.completeChildren();
        }
    }

    private void doRender(final RenderableDependency node, boolean last, Set<Object> visited) {
        // Do a shallow render of any constraint edges, and do not mark the node as visited.
        if (node.getResolutionState() == RenderableDependency.ResolutionState.RESOLVED_CONSTRAINT) {
            renderNode(node, last, false, dependenciesRenderer);
            legendRenderer.setHasConstraints(true);
            return;
        }

        final boolean alreadyRendered = !visited.add(node.getId());
        if (alreadyRendered) {
            legendRenderer.setHasCyclicDependencies(true);
        }

        renderNode(node, last, alreadyRendered, dependenciesRenderer);

        if (!alreadyRendered) {
            Set<? extends RenderableDependency> children = node.getChildren();
            renderChildren(children, visited);
        }

    }

    private void renderNode(final RenderableDependency node, final boolean isLast, final boolean isDuplicate, final NodeRenderer dependenciesRenderer) {
        renderer.visit(output -> dependenciesRenderer.renderNode(output, node, isDuplicate), isLast);
    }

    public void complete() {
        legendRenderer.printLegend();
    }
}
