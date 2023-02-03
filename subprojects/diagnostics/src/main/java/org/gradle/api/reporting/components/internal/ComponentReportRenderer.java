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

import com.google.common.collect.Sets;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.reporting.ReportRenderer;

import java.util.Collection;
import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class ComponentReportRenderer extends TextReportRenderer {
    private final ComponentRenderer componentRenderer;
    private final TrackingReportRenderer<LanguageSourceSet, TextReportBuilder> sourceSetRenderer;
    private final TrackingReportRenderer<BinarySpec, TextReportBuilder> binaryRenderer;

    public ComponentReportRenderer(FileResolver fileResolver, TypeAwareBinaryRenderer binaryRenderer) {
        setFileResolver(fileResolver);
        this.sourceSetRenderer = new TrackingReportRenderer<>(new SourceSetRenderer());
        this.binaryRenderer = new TrackingReportRenderer<>(binaryRenderer);
        this.componentRenderer = new ComponentRenderer(this.sourceSetRenderer, this.binaryRenderer);
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
            getBuilder().item(component, componentRenderer);
        }
    }

    public void renderSourceSets(Collection<LanguageSourceSet> sourceSets) {
        Set<LanguageSourceSet> additionalSourceSets = collectAdditionalSourceSets(sourceSets);
        outputCollection(additionalSourceSets, "Additional source sets", sourceSetRenderer, "source sets");
    }

   public void renderBinaries(Collection<BinarySpec> binaries) {
        Set<BinarySpec> additionalBinaries = collectAdditionalBinaries(binaries);
        outputCollection(additionalBinaries, "Additional binaries", binaryRenderer, "binaries");
    }

    private Set<LanguageSourceSet> collectAdditionalSourceSets(Collection<LanguageSourceSet> sourceSets) {
        Set<LanguageSourceSet> result = Sets.newTreeSet(SourceSetRenderer.SORT_ORDER);
        result.addAll(sourceSets);
        result.removeAll(sourceSetRenderer.getItems());
        return result;
    }

   private Set<BinarySpec> collectAdditionalBinaries(Collection<BinarySpec> binaries) {
       Set<BinarySpec> result = Sets.newTreeSet(TypeAwareBinaryRenderer.SORT_ORDER);
       result.addAll(binaries);
       result.removeAll(binaryRenderer.getItems());
       return result;
    }

    private <T> void outputCollection(Collection<? extends T> items, String title, ReportRenderer<T, TextReportBuilder> renderer, String elementsPlural) {
        if (!items.isEmpty()) {
            getBuilder().getOutput().println();
            getBuilder().collection(title, items, renderer, elementsPlural);
        }
    }
}
