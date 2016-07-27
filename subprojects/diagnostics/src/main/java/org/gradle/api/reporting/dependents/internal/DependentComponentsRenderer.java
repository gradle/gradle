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

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class DependentComponentsRenderer extends ReportRenderer<ComponentSpec, TextReportBuilder> {

    private final DependentBinariesResolver resolver;
    private final boolean showNonBuildable;
    private final boolean showTestSuites;

    private boolean seenTestSuite;
    private boolean hiddenTestSuite;
    private boolean hiddenNonBuildable;

    public DependentComponentsRenderer(@Nullable DependentBinariesResolver dependentBinariesResolver, boolean showNonBuildable, boolean showTestSuites) {
        this.resolver = dependentBinariesResolver;
        this.showNonBuildable = showNonBuildable;
        this.showTestSuites = showTestSuites;
    }

    @Override
    public void render(final ComponentSpec component, final TextReportBuilder builder) {
        ComponentSpecInternal internalProtocol = (ComponentSpecInternal) component;
        DependentComponentsRenderableDependency root = getRenderableDependencyOf(component, internalProtocol);
        if (!showNonBuildable && !root.isBuildable()) {
            hiddenNonBuildable = true;
            return;
        }
        StyledTextOutput output = builder.getOutput();
        GraphRenderer renderer = new GraphRenderer(output);
        renderer.visit(new Action<StyledTextOutput>() {
            @Override
            public void execute(StyledTextOutput output) {
                output.withStyle(Identifier).text(component.getName());
                output.withStyle(Description).text(" - Components that depend on " + component.getDisplayName());
            }
        }, true);
        DependentComponentsGraphRenderer dependentsGraphRenderer = new DependentComponentsGraphRenderer(renderer, showNonBuildable, showTestSuites);
        if (root.getChildren().isEmpty()) {
            output.withStyle(Info).text("No dependents");
            output.println();
        } else {
            dependentsGraphRenderer.render(root);
            output.println();
        }
        if (dependentsGraphRenderer.hasSeenTestSuite()) {
            seenTestSuite = true;
        }
        if (dependentsGraphRenderer.hasHiddenTestSuite()) {
            hiddenTestSuite = true;
        }
        if (dependentsGraphRenderer.hasHiddenNonBuildable()) {
            hiddenNonBuildable = true;
        }
    }

    private DependentComponentsRenderableDependency getRenderableDependencyOf(final ComponentSpec componentSpec, ComponentSpecInternal internalProtocol) {
        if (resolver != null && componentSpec instanceof VariantComponentSpec) {
            VariantComponentSpec variantComponentSpec = (VariantComponentSpec) componentSpec;
            LinkedHashSet<DependentComponentsRenderableDependency> children = Sets.newLinkedHashSet();
            for (BinarySpecInternal binarySpec : variantComponentSpec.getBinaries().withType(BinarySpecInternal.class)) {
                DependentBinariesResolutionResult resolvedBinary = resolver.resolve(binarySpec);
                children.add(DependentComponentsRenderableDependency.of(resolvedBinary.getRoot()));
            }
            return DependentComponentsRenderableDependency.of(componentSpec, internalProtocol, children);
        } else {
            return DependentComponentsRenderableDependency.of(componentSpec, internalProtocol);
        }
    }

    public void printLegend(TextReportBuilder builder) {
        if (seenTestSuite || hiddenTestSuite || hiddenNonBuildable) {
            StyledTextOutput output = builder.getOutput();
            if (seenTestSuite) {
                output.withStyle(Info).println("(t) - Test suite binary");
                if (hiddenNonBuildable) {
                    output.println();
                }
            } else if (hiddenTestSuite) {
                output.withStyle(Info).println("Some test suites were not shown, use --test-suites or --all to show them.");
            }
            if (hiddenNonBuildable) {
                output.withStyle(Info).println("Some non-buildable components were not shown, use --non-buildable or --all to show them.");
            }
        }
    }
}
