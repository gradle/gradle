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

import org.gradle.api.Project;
import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
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

    @Override
    public ReportRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(PropertyReportRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void generate(Project project) {
        for (Map.Entry<String, ?> entry : new TreeMap<String, Object>(project.getProperties()).entrySet()) {
            if (entry.getKey().equals("properties")) {
                renderer.addProperty(entry.getKey(), "{...}");
            } else {
                renderer.addProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
