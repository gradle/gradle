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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.platform.base.BinarySpec;
import org.gradle.reporting.ReportRenderer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.util.CollectionUtils;

import java.util.Comparator;

public class ComponentRenderer extends ReportRenderer<ComponentSpec, TextReportBuilder> {
    private final SourceSetRenderer sourceSetRenderer;
    private final BinaryRenderer renderer;

    public ComponentRenderer(FileResolver fileResolver) {
        sourceSetRenderer = new SourceSetRenderer(fileResolver);
        renderer = new BinaryRenderer(fileResolver);
    }

    @Override
    public void render(ComponentSpec component, TextReportBuilder builder) {
        builder.subheading(StringUtils.capitalize(component.getDisplayName()));
        builder.getOutput().println();
        builder.collection("Source sets", component.getSource(), sourceSetRenderer, "source sets");
        builder.getOutput().println();
        builder.collection("Binaries", CollectionUtils.sort(component.getBinaries(), new Comparator<BinarySpec>() {
            public int compare(BinarySpec binary1, BinarySpec binary2) {
                return binary1.getName().compareTo(binary2.getName());
            }
        }), renderer, "binaries");
    }
}
