/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.options;

import org.gradle.internal.reflect.JavaMethod;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class InstanceOptionDescriptor implements OptionDescriptor {

    private final Object object;
    private final OptionElement optionElement;
    private final JavaMethod<Object, Collection> optionValueMethod;

    InstanceOptionDescriptor(Object object, OptionElement optionElement) {
        this(object, optionElement, null);
    }

    public InstanceOptionDescriptor(Object object, OptionElement optionElement, JavaMethod<Object, Collection> optionValueMethod) {
        this.object = object;
        this.optionElement = optionElement;
        this.optionValueMethod = optionValueMethod;
    }

    @Override
    public String getName() {
        return optionElement.getOptionName();
    }

    @Override
    public Set<String> getAvailableValues() {
        final Set<String> values = optionElement.getAvailableValues();

        if (getArgumentType().isAssignableFrom(String.class)) {
            values.addAll(readDynamicAvailableValues());
        }
        return values;
    }

    @Override
    public Class<?> getArgumentType() {
        return optionElement.getOptionType();
    }

    private List<String> readDynamicAvailableValues() {
        if (optionValueMethod != null) {
            Collection values = optionValueMethod.invoke(object);
            return CollectionUtils.toStringList(values);
        }
        return Collections.emptyList();
    }

    @Override
    public String getDescription() {
        return optionElement.getDescription();
    }

    @Override
    public void apply(Object objectParam, List<String> parameterValues) {
        if (objectParam != object) {
            throw new AssertionError(String.format("Object %s not applyable. Expecting %s", objectParam, object));
        }
        optionElement.apply(objectParam, parameterValues);
    }

    @Override
    public int compareTo(OptionDescriptor o) {
        return getName().compareTo(o.getName());
    }
}
