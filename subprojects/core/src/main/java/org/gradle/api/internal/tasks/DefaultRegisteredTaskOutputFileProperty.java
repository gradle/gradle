/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

public class DefaultRegisteredTaskOutputFileProperty implements RegisteredTaskOutputFileProperty {
    private final ValidatingValue value;
    private boolean optional;
    private String propertyName;
    private final OutputFilePropertyType outputFilePropertyType;

    public DefaultRegisteredTaskOutputFileProperty(ValidatingValue value, OutputFilePropertyType outputFilePropertyType) {
        this.value = value;
        this.outputFilePropertyType = outputFilePropertyType;
    }

    @Override
    public TaskOutputFilePropertyBuilder withPropertyName(String propertyName) {
        this.propertyName = propertyName;
        return this;
    }

    @Override
    public TaskOutputFilePropertyBuilder optional() {
        return optional(true);
    }

    @Override
    public TaskOutputFilePropertyBuilder optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    @Override
    public ValidatingValue getValue() {
        return value;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public OutputFilePropertyType getPropertyType() {
        return outputFilePropertyType;
    }

    @Override
    public String toString() {
        return getPropertyName() + " (Output)";
    }
}
