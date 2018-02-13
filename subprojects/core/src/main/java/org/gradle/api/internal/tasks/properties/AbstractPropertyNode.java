/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import javax.annotation.Nullable;

abstract class AbstractPropertyNode<SELF extends AbstractPropertyNode<SELF>> implements PropertyNode<SELF> {
    private final String propertyName;
    private final Class<?> beanClass;

    public AbstractPropertyNode(@Nullable String propertyName, Class<?> beanClass) {
        this.propertyName = propertyName;
        this.beanClass = beanClass;
    }

    @Override
    public String getQualifiedPropertyName(String childPropertyName) {
        return propertyName == null ? childPropertyName : propertyName + "." + childPropertyName;
    }

    @Override
    public boolean isRoot() {
        return propertyName == null;
    }

    @Nullable
    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public String toString() {
        //noinspection ConstantConditions
        return isRoot() ? "<root>" : getPropertyName();
    }
}
