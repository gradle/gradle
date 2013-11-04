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

package org.gradle.api.internal.tasks;

import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class InstanceCommandLineOptionDescriptor implements CommandLineOptionDescriptor {

    private final Object object;
    private final CommandLineOptionDescriptor delegate;

    public InstanceCommandLineOptionDescriptor(Object object, CommandLineOptionDescriptor delegate) {
        this.object = object;
        this.delegate = delegate;
    }

    public String getName() {
        return delegate.getName();
    }

    public CommandLineOption getOption() {
        return delegate.getOption();
    }

    public Method getConfigurationMethod() {
        return delegate.getConfigurationMethod();
    }

    public List<String> getAvailableValues() {
        final List<String> values = delegate.getAvailableValues();

        if (String.class.isAssignableFrom(getArgumentType())) {
            values.addAll(lookupDynamicAvailableValues());
        }
        return values;
    }

    public Class getArgumentType() {
        return delegate.getArgumentType();
    }

    private List<String> lookupDynamicAvailableValues() {
        for (Class<?> type = object.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (Collection.class.isAssignableFrom(method.getReturnType()) && method.getParameterTypes().length == 0) {
                    OptionValues optionValues = method.getAnnotation(OptionValues.class);
                    if (optionValues != null && optionValues.value()[0].equals(getName())) {
                        final JavaMethod<Object, Object> methodToInvoke = JavaReflectionUtil.method(Object.class, Object.class, method);
                        List values = (List) methodToInvoke.invoke(object);
                        return CollectionUtils.stringize(values);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    public String getDescription() {
        return getOption().description();
    }

    public void apply(Object objectParam, List<String> parameterValues) {
        if (objectParam != object) {
            throw new AssertionError(String.format("Object %s not applyable. Expecting %s", objectParam, object));
        }
        delegate.apply(objectParam, parameterValues);
    }

    public int compareTo(CommandLineOptionDescriptor o) {
        return delegate.compareTo(o);
    }
}