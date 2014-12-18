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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.logging.StyledTextOutput.Style.Info;

public class ComponentReportRenderer extends TextReportRenderer {
    private final ComponentRenderer componentRenderer;
    private final SourceSetRenderer sourceSetRenderer;
    private final TypeAwareBinaryRenderer binaryRenderer;
    private final Set<LanguageSourceSet> componentSourceSets = new HashSet<LanguageSourceSet>();
    private final Set<BinarySpec> componentBinaries = new HashSet<BinarySpec>();

    public ComponentReportRenderer(FileResolver fileResolver, TypeAwareBinaryRenderer binaryRenderer) {
        setFileResolver(fileResolver);
        sourceSetRenderer = new SourceSetRenderer();
        this.binaryRenderer = binaryRenderer;
        componentRenderer = new ComponentRenderer(sourceSetRenderer, binaryRenderer);
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

    public void renderSourceSets(Collection<LanguageSourceSet> sourceSets) {
        Set<LanguageSourceSet> additionalSourceSets = new LinkedHashSet<LanguageSourceSet>();
        for (LanguageSourceSet sourceSet : sourceSets) {
            if (!componentSourceSets.contains(sourceSet)) {
                additionalSourceSets.add(sourceSet);
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
                try {
                    binaryRenderer.render(binary, getBuilder());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
