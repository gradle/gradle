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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MethodOptionElement implements OptionElement {

    private final Method method;
    private ArrayList<String> availableValues;
    private Class<?> argumentType;

    public MethodOptionElement(String optionName, Method method) {
        assertMethodTypeSupported(optionName, method);
        this.method = method;
    }

    public Class<?> getDeclaredClass() {
        return method.getDeclaringClass();
    }

    public List<String> getAvailableValues() {
        //calculate list lazy to avoid overhead upfront
        if (availableValues == null) {
            calculdateAvailableValuesAndTypes();
        }
        return availableValues;
    }

    public Class<?> getOptionType() {
        //calculate lazy to avoid overhead upfront
        if (argumentType == null) {
            calculdateAvailableValuesAndTypes();
        }
        return argumentType;
    }

    public String getName() {
        return method.getName();
    }

    public void apply(Object object, List<String> parameterValues) {
        final JavaMethod<Object, Object> javaMethod = JavaReflectionUtil.method(Object.class, Object.class, method);
        if (parameterValues.size() == 0) {
            javaMethod.invoke(object, true);
        } else if (parameterValues.size() > 1) {
            //TODO add List<String> support
            //TODO propagate this exception
            throw new IllegalArgumentException(String.format("Lists not supported for option"));
        } else {
            Object arg = getParameterObject(parameterValues.get(0));
            javaMethod.invoke(object, arg);
        }
    }

    private Object getParameterObject(String value) {
        if (getOptionType().isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) getOptionType(), value);
        }
        return value;
    }


    private void calculdateAvailableValuesAndTypes() {
        availableValues = new ArrayList<String>();
        if (method.getParameterTypes().length == 1) {
            Class<?> type = method.getParameterTypes()[0];
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
                }
            }
        } else {
            argumentType = Void.TYPE;
        }
    }

    private static void assertMethodTypeSupported(String optionName, Method method) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1) {
            throw new OptionValidationException(String.format("Option '%s' cannot be linked to methods with multiple parameters in class '%s#%s'.",
                    optionName, method.getDeclaringClass().getName(), method.getName()));
        }

        if (parameterTypes.length == 1) {
            final Class<?> parameterType = parameterTypes[0];
            if (!(parameterType == Boolean.class || parameterType == Boolean.TYPE)
                    && !parameterType.isAssignableFrom(String.class)
                    && !parameterType.isEnum()) {
                throw new OptionValidationException(String.format("Option '%s' cannot be casted to parameter type '%s' in class '%s'.",
                        optionName, parameterType.getName(), method.getDeclaringClass().getName()));
            }
        }
    }
}
