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
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class InstanceOptionDescriptor implements OptionDescriptor {

    private final Object object;
    private final OptionElement optionElement;

    public InstanceOptionDescriptor(Object object, OptionElement optionElement) {
        this.object = object;
        this.optionElement = optionElement;
    }

    public OptionElement getOptionElement() {
        return optionElement;
    }

    public String getName() {
        return optionElement.getOptionName();
    }

    public List<String> getAvailableValues() {
        final List<String> values = optionElement.getAvailableValues();

        if (getArgumentType().isAssignableFrom(String.class)) {
            values.addAll(readDynamicAvailableValues());
        }
        return values;
    }

    public Class<?> getArgumentType() {
        return optionElement.getOptionType();
    }

    private List<String> readDynamicAvailableValues() {
        JavaMethod<Object, Collection> optionValueMethod = getOptionValueMethod(object, getName());
        if (optionValueMethod != null) {
            Collection values = optionValueMethod.invoke(object);
            return CollectionUtils.toStringList(values);
        }
        return Collections.emptyList();
    }

    public String getDescription() {
        return optionElement.getDescription();
    }

    public void apply(Object objectParam, List<String> parameterValues) {
        if (objectParam != object) {
            throw new AssertionError(String.format("Object %s not applyable. Expecting %s", objectParam, object));
        }
        optionElement.apply(objectParam, parameterValues);
    }

    public int compareTo(OptionDescriptor o) {
        return getName().compareTo(o.getName());
    }

    private static JavaMethod<Object, Collection> getOptionValueMethod(Object object, String name) {
        JavaMethod<Object, Collection> optionValueMethod = null;
        for (Class<?> type = object.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                OptionValues optionValues = method.getAnnotation(OptionValues.class);
                if (optionValues != null) {
                    if (Collection.class.isAssignableFrom(method.getReturnType())
                            && method.getParameterTypes().length == 0
                            && !Modifier.isStatic(method.getModifiers())) {
                        if (CollectionUtils.toList(optionValues.value()).contains(name)) {
                            if (optionValueMethod == null) {
                                optionValueMethod = JavaReflectionUtil.method(Object.class, Collection.class, method);
                            } else {
                                throw new OptionValidationException(
                                        String.format("OptionValues for '%s' cannot be attached to multiple methods in class '%s'.",
                                                name,
                                                type.getName()));
                            }
                        }
                    } else {
                        throw new OptionValidationException(
                                String.format("OptionValues annotation not supported on method '%s' in class '%s'. Supported method must be non static, return Collection and take no parameters.",
                                        method.getName(),
                                        type.getName()));
                    }
                }
            }
        }
        return optionValueMethod;
    }
}