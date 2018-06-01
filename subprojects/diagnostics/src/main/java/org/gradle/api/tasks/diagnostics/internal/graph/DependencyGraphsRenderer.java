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
            renderer.visit(new Action<StyledTextOutput>() {
                @Override
                public void execute(StyledTextOutput styledTextOutput) {
                    rootRenderer.renderNode(styledTextOutput, root, false);
                }
            }, true);
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
        Set<? extends RenderableDependency> children = node.getChildren();
        final boolean alreadyRendered = !visited.add(node.getId());
        if (alreadyRendered) {
            legendRenderer.setHasCyclicDependencies(true);
        }

        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput output) {
                dependenciesRenderer.renderNode(output, node, alreadyRendered);
            }

        }, last);

        if (!alreadyRendered) {
            renderChildren(children, visited);
        }

    }

    public void complete() {
        legendRenderer.printLegend();
    }
}
