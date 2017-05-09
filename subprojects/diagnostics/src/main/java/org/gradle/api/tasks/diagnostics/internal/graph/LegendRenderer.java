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

import org.gradle.internal.logging.text.StyledTextOutput;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class LegendRenderer {
    private final StyledTextOutput output;

    private boolean hasCyclicDependencies;
    private boolean hasUnresolvableConfigurations;

    public LegendRenderer(StyledTextOutput output) {
        this.output = output;
    }

    public void printLegend() {
        if (hasCyclicDependencies) {
            output.println();
            output.withStyle(Info).println("(*) - dependencies omitted (listed previously)");
        }
        if (hasUnresolvableConfigurations) {
            output.println();
            output.withStyle(Info).println("(n) - Not resolved (configuration is not meant to be resolved)");
        }
    }

    public void setHasUnresolvableConfigurations(boolean hasUnresolvableConfigurations) {
        this.hasUnresolvableConfigurations = hasUnresolvableConfigurations;
    }

    public void setHasCyclicDependencies(boolean hasCyclicDependencies) {
        this.hasCyclicDependencies = hasCyclicDependencies;
    }
}
