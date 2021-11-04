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
package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;

/**
 * <p>A {@code PropertyReportRenderer} is responsible for rendering the model of a property report.</p>
 */
public class PropertyReportRenderer extends TextReportRenderer {
    private static final Logger LOGGER = Logging.getLogger(PropertyReportRenderer.class);

    /**
     * Writes a property for the current project.
     *
     * @param name The name of the property
     * @param value The value of the property
     */
    public void addProperty(String name, @Nullable Object value) {
        String strValue;
        try {
            strValue = String.valueOf(value);
        } catch (Exception e) {
            String valueClass = value != null ? String.valueOf(value.getClass()) : "null";
            LOGGER.warn("Rendering of the property '{}' with value type '{}' failed with exception", name, valueClass, e);
            strValue = valueClass + " [Rendering failed]";
        }
        getTextOutput().formatln("%s: %s", name, strValue);
    }
}
