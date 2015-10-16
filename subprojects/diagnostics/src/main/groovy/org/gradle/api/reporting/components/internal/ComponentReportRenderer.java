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
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;

import java.util.Collection;
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
        Set<LanguageSourceSet> additionalSourceSets = Sets.newTreeSet(SourceSetRenderer.SORT_ORDER);
        for (LanguageSourceSet sourceSet : sourceSets) {
            if (!componentSourceSets.contains(sourceSet)) {
                additionalSourceSets.add(sourceSet);
            }
        }
        if (!additionalSourceSets.isEmpty()) {
            getBuilder().getOutput().println();
            getBuilder().collection("Additional source sets", additionalSourceSets, sourceSetRenderer, "source sets");
        }
    }

    public void renderBinaries(Collection<BinarySpec> binaries) {
        Set<BinarySpec> additionalBinaries = Sets.newTreeSet(TypeAwareBinaryRenderer.SORT_ORDER);
        for (BinarySpec binary : binaries) {
            if (!componentBinaries.contains(binary)) {
                additionalBinaries.add(binary);
            }
        }
        if (!additionalBinaries.isEmpty()) {
            getBuilder().getOutput().println();
            getBuilder().collection("Additional binaries", additionalBinaries, binaryRenderer, "binaries");
        }
    }
}
