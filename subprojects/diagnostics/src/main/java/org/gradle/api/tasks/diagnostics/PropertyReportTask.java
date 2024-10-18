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
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.Pair;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Displays the properties of a project. An instance of this type is used when you execute the {@code properties} task
 * from the command-line.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PropertyReportTask extends AbstractProjectBasedReportTask<PropertyReportTask.PropertyReportModel> {

    public PropertyReportTask() {
        getRenderer().convention(new PropertyReportRenderer()).finalizeValueOnRead();
    }

    /**
     * Defines a specific property to report. If not set then all properties will appear in the report.
     *
     * @since 7.5
     */
    @Input
    @Optional
    @Option(option = "property", description = "A specific property to output")
    public abstract Property<String> getProperty();

    @Internal
    @Override
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = ReplacedAccessor.AccessorType.SETTER, name = "setRenderer", originalType = PropertyReportRenderer.class)
    })
    public abstract Property<PropertyReportRenderer> getRenderer();

    @Override
    protected PropertyReportModel calculateReportModelFor(Project project) {
        return computePropertyReportModel(project);
    }

    private PropertyReportTask.PropertyReportModel computePropertyReportModel(Project project) {
        PropertyReportModel model = new PropertyReportModel();
        Map<String, ?> projectProperties = project.getProperties();
        if (getProperty().isPresent()) {
            String propertyName = getProperty().get();
            if ("properties".equals(propertyName)) {
                model.putProperty(propertyName, "{...}");
            } else {
                model.putProperty(propertyName, projectProperties.get(propertyName));
            }
        } else {
            for (Map.Entry<String, ?> entry : new TreeMap<>(projectProperties).entrySet()) {
                if ("properties".equals(entry.getKey())) {
                    model.putProperty(entry.getKey(), "{...}");
                } else {
                    model.putProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        return model;
    }

    @Override
    protected void generateReportFor(ProjectDetails project, PropertyReportModel model) {
        for (PropertyWarning warning : model.warnings) {
            getLogger().warn(
                "Rendering of the property '{}' with value type '{}' failed with exception",
                warning.name, warning.valueClass, warning.exception
            );
        }
        for (Pair<String, String> entry : model.properties) {
            getRenderer().get().addProperty(entry.getLeft(), entry.getRight());
        }
    }

    /**
     * Model for the report.
     *
     * @since 7.6
     */
    @Incubating
    public static final class PropertyReportModel {

        private PropertyReportModel() {
        }

        private final List<PropertyWarning> warnings = new ArrayList<>();
        private final List<Pair<String, String>> properties = new ArrayList<>();

        private void putProperty(String name, @Nullable Object value) {
            String strValue;
            try {
                strValue = String.valueOf(value);
            } catch (Exception e) {
                String valueClass = value != null ? String.valueOf(value.getClass()) : "null";
                warnings.add(new PropertyWarning(name, valueClass, e));
                strValue = valueClass + " [Rendering failed]";
            }
            properties.add(Pair.of(name, strValue));
        }
    }

    private static class PropertyWarning {
        private final String name;
        private final String valueClass;
        private final Exception exception;

        private PropertyWarning(String name, String valueClass, Exception exception) {
            this.name = name;
            this.valueClass = valueClass;
            this.exception = exception;
        }
    }
}
