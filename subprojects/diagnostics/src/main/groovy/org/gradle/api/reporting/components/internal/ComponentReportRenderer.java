/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.BinarySpec;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.logging.StyledTextOutput.Style.Info;

public class ComponentReportRenderer extends TextReportRenderer {
    private final ComponentRenderer componentRenderer;
    private final SourceSetRenderer sourceSetRenderer;
    private final BinaryRenderer binaryRenderer;
    private final Set<LanguageSourceSet> componentSourceSets = new HashSet<LanguageSourceSet>();
    private final Set<BinarySpec> componentBinaries = new HashSet<BinarySpec>();

    public ComponentReportRenderer(FileResolver fileResolver) {
        componentRenderer = new ComponentRenderer(fileResolver);
        sourceSetRenderer = new SourceSetRenderer(fileResolver);
        binaryRenderer = new BinaryRenderer(fileResolver);
    }

    @Override
    public void complete() {
        getTextOutput().println();
        getTextOutput().println("Note: currently not all plugins register their components, so some components may not be visible here.");
        super.complete();
    }

    public void renderComponents(Collection<ComponentSpec> components) {
        if (components.isEmpty()) {
            getTextOutput().withStyle(Info).println("No components defined for this project.");
            return;
        }
        boolean seen = false;
        for (ComponentSpec component : components) {
            if (seen) {
                getBuilder().getOutput().println();
            } else {
                seen = true;
            }
            componentRenderer.render(component, getBuilder());
            componentSourceSets.addAll(component.getSource());
            componentBinaries.addAll(component.getBinaries());
        }
    }

    public void renderSourceSets(Collection<FunctionalSourceSet> sourceSets) {
        Set<LanguageSourceSet> additionalSourceSets = new LinkedHashSet<LanguageSourceSet>();
        for (FunctionalSourceSet functionalSourceSet : sourceSets) {
            for (LanguageSourceSet sourceSet : functionalSourceSet) {
                if (!componentSourceSets.contains(sourceSet)) {
                    additionalSourceSets.add(sourceSet);
                }
            }
        }
        if (!additionalSourceSets.isEmpty()) {
            getBuilder().getOutput().println();
            getBuilder().subheading("Additional source sets");
            for (LanguageSourceSet sourceSet : additionalSourceSets) {
                sourceSetRenderer.render(sourceSet, getBuilder());
            }
        }
    }

    public void renderBinaries(Collection<BinarySpec> binaries) {
        Set<BinarySpec> additionalBinaries = new LinkedHashSet<BinarySpec>();
        for (BinarySpec binary : binaries) {
            if (!componentBinaries.contains(binary)) {
                additionalBinaries.add(binary);
            }
        }
        if (!additionalBinaries.isEmpty()) {
            getBuilder().getOutput().println();
            getBuilder().subheading("Additional binaries");
            for (BinarySpec binary : additionalBinaries) {
                binaryRenderer.render(binary, getBuilder());
            }
        }
    }
}
