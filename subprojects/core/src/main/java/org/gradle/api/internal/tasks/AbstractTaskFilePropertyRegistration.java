/*
 * Copyright 2019 the original author or authors.
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

public abstract class AbstractTaskFilePropertyRegistration implements TaskPropertyRegistration {
    private String propertyName;
    private boolean optional;
    private final StaticValue value;

    public AbstractTaskFilePropertyRegistration(StaticValue value) {
        this.value = value;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public StaticValue getValue() {
        return value;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = TaskPropertyUtils.checkPropertyName(propertyName);
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }
}
