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
import java.util.Comparator;
import java.util.HashSet;
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
            getBuilder().item(component, componentRenderer);
            componentSourceSets.addAll(component.getSources().values());
            componentBinaries.addAll(component.getBinaries().values());
        }
    }

    public void renderSourceSets(Collection<LanguageSourceSet> sourceSets) {
        Set<LanguageSourceSet> additionalSourceSets = collectAdditionalSourceSets(sourceSets);
        outputCollection(additionalSourceSets, "Additional source sets", sourceSetRenderer, "source sets");
    }

   public void renderBinaries(Iterable<BinarySpec> binaries) {
        Set<BinarySpec> additionalBinaries = collectAdditionalBinaries(binaries);
        outputCollection(additionalBinaries, "Additional binaries", binaryRenderer, "binaries");
    }

    private Set<LanguageSourceSet> collectAdditionalSourceSets(Collection<LanguageSourceSet> sourceSets) {
        return elementsNotIn(componentSourceSets, sourceSets, SourceSetRenderer.SORT_ORDER);
    }

   private Set<BinarySpec> collectAdditionalBinaries(Iterable<BinarySpec> binaries) {
        return elementsNotIn(componentBinaries, binaries, TypeAwareBinaryRenderer.SORT_ORDER);
    }

    private static <T> Set<T> elementsNotIn(Set<T> set, Iterable<T> elements, Comparator<? super T> comparator) {
        Set<T> result = Sets.newTreeSet(comparator);
        for (T element : elements) {
            if (!set.contains(element)) {
                result.add(element);
            }
        }
        return result;
    }

    private <T> void outputCollection(Collection<? extends T> items, String title, ReportRenderer<T, TextReportBuilder> renderer, String elementsPlural) {
        if (!items.isEmpty()) {
            getBuilder().getOutput().println();
            getBuilder().collection(title, items, renderer, elementsPlural);
        }
    }
}
