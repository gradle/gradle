/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import java.util.Map;
import java.util.TreeMap;

/**
 * Displays the properties of a project. An instance of this type is used when you execute the {@code properties} task
 * from the command-line.
 */
@DisableCachingByDefault(because = "Not worth caching")
public class PropertyReportTask extends ProjectBasedReportTask {
    private PropertyReportRenderer renderer = new PropertyReportRenderer();
    private final Property<String> property = getProject().getObjects().property(String.class);

    /**
     * Defines a specific property to report. If not set then all properties will appear in the report.
     *
     * @since 7.5
     */
    @Incubating
    @Input
    @Optional
    @Option(option = "property", description = "A specific property to output")
    public Property<String> getProperty() {
        return property;
    }

    @Override
    public ReportRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(PropertyReportRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void generate(Project project) {
        Map<String, Object> entries = new TreeMap<>(project.getProperties());
        if (property.isPresent()) {
            String propertyValue = property.get();
            if (propertyValue.equals("properties")) {
                renderer.addProperty(propertyValue, "{...}");
            } else {
                renderer.addProperty(propertyValue, entries.get(propertyValue));
            }
        } else {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                if (entry.getKey().equals("properties")) {
                    renderer.addProperty(entry.getKey(), "{...}");
                } else {
                    renderer.addProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
