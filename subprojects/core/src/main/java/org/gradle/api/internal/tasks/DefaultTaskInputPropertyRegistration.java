/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.internal.properties.StaticValue;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultTaskInputPropertyRegistration implements TaskInputPropertyRegistration {

    private final String propertyName;
    private final StaticValue value;
    private boolean optional;

    public DefaultTaskInputPropertyRegistration(String propertyName, StaticValue value) {
        this.propertyName = propertyName;
        this.value = value;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public TaskInputPropertyBuilder optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    @Override
    public StaticValue getValue() {
        return value;
    }

    @Override
    public String toString() {
        return propertyName;
    }
}
