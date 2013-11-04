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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CommandLineOptionDescriptor implements Comparable<CommandLineOptionDescriptor> {
    private final CommandLineOption option;
    private final Method annotatedMethod;
    private final Object object;
    private List<String> availableValues;
    private Class argumentType;

    public CommandLineOptionDescriptor(Object object, CommandLineOption commandLineOption, Method method) {
        this.object = object;
        this.option = commandLineOption;
        this.annotatedMethod = method;
    }

    public String getName() {
        return option.options()[0];
    }

    public CommandLineOption getOption() {
        return option;
    }

    public Method getAnnotatedMethod() {
        return annotatedMethod;
    }

    public List<String> getAvailableValues() {
        //calculate list lazy to avoid overhead upfront
        if (availableValues == null) {
            calculdateAvailableValuesAndTypes();
        }
        return availableValues;
    }

    public Class getArgumentType() {
        //calculate lazy to avoid overhead upfront
        if (argumentType == null) {
            calculdateAvailableValuesAndTypes();
        }
        return argumentType;
    }

    private void calculdateAvailableValuesAndTypes() {
        availableValues = new ArrayList<String>();
        if (annotatedMethod.getParameterTypes().length == 1) {
            Class<?> type = annotatedMethod.getParameterTypes()[0];
            //we don't want to support "--flag true" syntax
            if (type == Boolean.class || type == Boolean.TYPE) {
                argumentType = Void.TYPE;
            } else {
                argumentType = type;
                if (argumentType.isEnum()) {
                    final Enum[] enumConstants = (Enum[]) argumentType.getEnumConstants();
                    for (Enum enumConstant : enumConstants) {
                        availableValues.add(enumConstant.name());
                    }
                } else if (String.class.isAssignableFrom(argumentType)) {
                    availableValues.addAll(lookupDynamicAvailableValues());
                }
            }
        } else {
            argumentType = Void.TYPE;
        }
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

    public int compareTo(CommandLineOptionDescriptor o) {
        return option.options()[0].compareTo(o.option.options()[0]);
    }

    public String getDescription() {
        return getOption().description();
    }

    public void apply(List<String> parameterValues) {
        final JavaMethod<Object, Object> method = JavaReflectionUtil.method(Object.class, Object.class, annotatedMethod);
        if (parameterValues.size() == 0) {
            method.invoke(object, true);
        } else if (parameterValues.size() > 1) {
            //TODO add List<String> support
            throw new IllegalArgumentException(String.format("Lists not supported for option '%s'.", getName()));
        } else {
            Object arg = getParameterObject(parameterValues.get(0));
            method.invoke(object, arg);
        }
    }

    private Object getParameterObject(String value) {
        if (getArgumentType().isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) getArgumentType(), value);
        }
        return value;
    }
}