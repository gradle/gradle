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

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.VariantComponentSpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolutionResult;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;
import org.gradle.reporting.ReportRenderer;

import java.util.LinkedHashSet;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.*;

public class DependentComponentsRenderer extends ReportRenderer<ComponentSpec, TextReportBuilder> {

    private final DependentBinariesResolver resolver;

    public DependentComponentsRenderer(@Nullable DependentBinariesResolver dependentBinariesResolver) {
        this.resolver = dependentBinariesResolver;
    }

    @Override
    public void render(final ComponentSpec component, final TextReportBuilder builder) {
        ComponentSpecInternal internalProtocol = (ComponentSpecInternal) component;
        StyledTextOutput output = builder.getOutput();
        GraphRenderer renderer = new GraphRenderer(output);
        renderer.visit(new Action<StyledTextOutput>() {
            @Override
            public void execute(StyledTextOutput output) {
                output.withStyle(Identifier).text(component.getName());
                output.withStyle(Description).text(" - Dependent components for " + component.getDisplayName());
            }
        }, true);
        NodeRenderer nodeRenderer = new DependentBinaryNodeRenderer();
        DependencyGraphRenderer dependencyGraphRenderer = new DependencyGraphRenderer(renderer, nodeRenderer);
        RenderableDependency root = getRenderableDependencyOf(component, internalProtocol);
        if (root.getChildren().isEmpty()) {
            output.withStyle(Info).text("No dependents");
            output.println();
        } else {
            dependencyGraphRenderer.render(root);
        }
    }

    private RenderableDependency getRenderableDependencyOf(final ComponentSpec componentSpec, ComponentSpecInternal internalProtocol) {
        if (resolver != null && componentSpec instanceof VariantComponentSpec) {
            VariantComponentSpec variantComponentSpec = (VariantComponentSpec) componentSpec;
            LinkedHashSet<DependentComponentsRenderableDependency> children = Sets.newLinkedHashSet();
            for (BinarySpecInternal binarySpec : variantComponentSpec.getBinaries().withType(BinarySpecInternal.class)) {
                DependentBinariesResolutionResult resolvedBinary = resolver.resolve(binarySpec, false);
                children.add(DependentComponentsRenderableDependency.of(resolvedBinary.getRoot()));
            }
            return DependentComponentsRenderableDependency.of(componentSpec, internalProtocol, children);
        } else {
            return DependentComponentsRenderableDependency.of(componentSpec, internalProtocol);
        }
    }

    private static class DependentBinaryNodeRenderer implements NodeRenderer {
        @Override
        public void renderNode(StyledTextOutput output, RenderableDependency node, boolean alreadyRendered) {
            output.text(node.getName());
            if (node instanceof DependentComponentsRenderableDependency) {
                DependentComponentsRenderableDependency dep = Cast.uncheckedCast(node);
                if (!dep.isBuildable()) {
                    output.withStyle(Info).text(" NOT BUILDABLE");
                }
            }
        }
    }
}
