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

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.internal.CollectionUtils;

@SuppressWarnings("deprecation")
public class ComponentRenderer extends ReportRenderer<org.gradle.platform.base.ComponentSpec, TextReportBuilder> {
    private final ReportRenderer<org.gradle.language.base.LanguageSourceSet, TextReportBuilder> sourceSetRenderer;
    private final ReportRenderer<org.gradle.platform.base.BinarySpec, TextReportBuilder> binaryRenderer;

    public ComponentRenderer(ReportRenderer<org.gradle.language.base.LanguageSourceSet, TextReportBuilder> sourceSetRenderer, ReportRenderer<org.gradle.platform.base.BinarySpec, TextReportBuilder> binaryRenderer) {
        this.sourceSetRenderer = sourceSetRenderer;
        this.binaryRenderer = binaryRenderer;
    }

    @Override
    public void render(org.gradle.platform.base.ComponentSpec component, TextReportBuilder builder) {
        builder.heading(StringUtils.capitalize(component.getDisplayName()));
        if (component instanceof org.gradle.platform.base.SourceComponentSpec) {
            org.gradle.platform.base.SourceComponentSpec sourceComponentSpec = (org.gradle.platform.base.SourceComponentSpec) component;
            builder.getOutput().println();
            builder.collection("Source sets", CollectionUtils.sort(sourceComponentSpec.getSources().values(), SourceSetRenderer.SORT_ORDER), sourceSetRenderer, "source sets");
        }
        if (component instanceof org.gradle.platform.base.VariantComponentSpec) {
            org.gradle.platform.base.VariantComponentSpec variantComponentSpec = (org.gradle.platform.base.VariantComponentSpec) component;
            builder.getOutput().println();
            builder.collection("Binaries", CollectionUtils.sort(variantComponentSpec.getBinaries().values(), TypeAwareBinaryRenderer.SORT_ORDER), binaryRenderer, "binaries");
        }
    }
}
