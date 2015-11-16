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

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.platform.base.BinarySpec;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class TypeAwareBinaryRenderer extends ReportRenderer<BinarySpec, TextReportBuilder> {
    static final Comparator<BinarySpec> SORT_ORDER = new Comparator<BinarySpec>() {
        public int compare(BinarySpec binary1, BinarySpec binary2) {
            return binary1.getName().compareTo(binary2.getName());
        }
    };
    private final Map<Class<?>, ReportRenderer<BinarySpec, TextReportBuilder>> renderers = new HashMap<Class<?>, ReportRenderer<BinarySpec, TextReportBuilder>>();

    public void register(AbstractBinaryRenderer<?> renderer) {
        renderers.put(renderer.getTargetType(), renderer);
    }

    @Override
    public void render(BinarySpec model, TextReportBuilder output) throws IOException {
        ReportRenderer<BinarySpec, TextReportBuilder> renderer = getRendererForType(model.getClass());
        renderer.render(model, output);
    }

    private ReportRenderer<BinarySpec, TextReportBuilder> getRendererForType(Class<? extends BinarySpec> type) {
        ReportRenderer<BinarySpec, TextReportBuilder> renderer = renderers.get(type);
        if (renderer == null) {
            Class<?> bestType = null;
            for (Map.Entry<Class<?>, ReportRenderer<BinarySpec, TextReportBuilder>> entry : renderers.entrySet()) {
                if (!entry.getKey().isAssignableFrom(type)) {
                    continue;
                }
                if (bestType == null || bestType.isAssignableFrom(entry.getKey())) {
                    bestType = entry.getKey();
                    renderer = entry.getValue();
                }
            }
            renderers.put(type, renderer);
        }
        return renderer;
    }
}
