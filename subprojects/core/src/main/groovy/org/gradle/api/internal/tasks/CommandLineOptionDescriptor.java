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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandLineOptionDescriptor implements Comparable<CommandLineOptionDescriptor> {
    private CommandLineOption option;
    private Method annotatedMethod;
    private List<String> availableValues;
    private Class argumentType;

    public CommandLineOptionDescriptor(CommandLineOption option, Method annotatedMethod) {
        this.option = option;
        this.annotatedMethod = annotatedMethod;
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
        if (annotatedMethod.getParameterTypes().length == 1) {
            Class<?> type = annotatedMethod.getParameterTypes()[0];
            //we don't want support for "--flag true" syntax
            if (type == Boolean.class || type == Boolean.TYPE) {
                argumentType = Void.TYPE;
            }else{
                argumentType = type;
            }
            availableValues = new ArrayList<String>();
        } else {
            // TODO deal correctly with annotated methods
            // with multiple parameters
            availableValues = Collections.EMPTY_LIST;
            argumentType = Void.TYPE;
        }
    }

    public int compareTo(CommandLineOptionDescriptor o) {
        return option.options()[0].compareTo(o.option.options()[0]);
    }

    public String getDescription() {
        return getOption().description();
    }

    public void apply(Object objectToConfigure, List<String> parameterValues) {
        final JavaMethod<Object, Object> method = JavaReflectionUtil.method(Object.class, Object.class, annotatedMethod);
        if (parameterValues.size() == 0) {
            method.invoke(objectToConfigure, true);
        }else if (parameterValues.size() > 1) {
            //TODO add List<String> support
            throw new IllegalArgumentException(String.format("Lists not supported for option '%s'.", getName()));
        } else{
            Object arg = getParameterObject(parameterValues.get(0));
            method.invoke(objectToConfigure, arg);
        }
    }

    private Object getParameterObject(String value) {
        if (getArgumentType().isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) getArgumentType(), value);
        }
        return value;
    }
}