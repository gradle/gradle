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
import java.util.List;

public class StaticCommandLineOptionDescriptor implements CommandLineOptionDescriptor {
    private final CommandLineOption option;
    private final Method configurationMethod;
    private List<String> availableValues;
    private Class argumentType;

    public StaticCommandLineOptionDescriptor(CommandLineOption commandLineOption, Method method) {
        this.option = commandLineOption;
        this.configurationMethod = method;
    }

    public String getName() {
        return option.options()[0];
    }

    public Method getConfigurationMethod() {
        return configurationMethod;
    }

    public CommandLineOption getOption() {
        return option;
    }

    public String getDescription() {
        return getOption().description();
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
        if (configurationMethod.getParameterTypes().length == 1) {
            Class<?> type = configurationMethod.getParameterTypes()[0];
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

    public void apply(Object object, List<String> parameterValues) {
        final JavaMethod<Object, Object> method = JavaReflectionUtil.method(Object.class, Object.class, getConfigurationMethod());
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

    public int compareTo(CommandLineOptionDescriptor o) {
        return getOption().options()[0].compareTo(o.getOption().options()[0]);
    }
}
