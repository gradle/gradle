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

import org.gradle.api.Nullable;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;

import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class TextDependentComponentsReportRenderer extends TextReportRenderer {

    private final DependentComponentsRenderer dependentComponentsRenderer;

    public TextDependentComponentsReportRenderer(@Nullable DependentBinariesResolver dependentBinariesResolver, boolean showNonBuildable, boolean showTestSuites) {
        this.dependentComponentsRenderer = new DependentComponentsRenderer(dependentBinariesResolver, showNonBuildable, showTestSuites);
    }

    public void renderComponents(Set<ComponentSpec> components) {
        if (components.isEmpty()) {
            getTextOutput().withStyle(Info).println("No components.");
            return;
        }
        for (ComponentSpec component : components) {
            getBuilder().item(component, dependentComponentsRenderer);
        }
    }

    public void renderLegend() {
        dependentComponentsRenderer.printLegend(getBuilder());
    }
}
