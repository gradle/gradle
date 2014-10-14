/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.ModelType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

public class ModelSchemaExtractor {

    public <T> ModelSchema<T> extract(Class<T> type) throws InvalidManagedModelElementTypeException {
        validateType(type);

        List<Method> methodList = Arrays.asList(type.getDeclaredMethods());
        if (methodList.isEmpty()) {
            return new ModelSchema<T>(type, Collections.<ModelProperty<?>>emptyList());
        }

        List<ModelProperty<?>> properties = Lists.newLinkedList();

        Map<String, Method> methods = Maps.newHashMap();
        for (Method method : methodList) {
            String name = method.getName();
            if (methods.containsKey(name)) {
                throw invalidMethod(type, name, "overloaded methods are not supported");
            }
            methods.put(name, method);
        }

        List<String> methodNames = Lists.newLinkedList(methods.keySet());
        List<String> handled = Lists.newArrayList();

        for (ListIterator<String> iterator = methodNames.listIterator(); iterator.hasNext();) {
            String methodName = iterator.next();

            Method method = methods.get(methodName);
            if (methodName.startsWith("get") && !methodName.equals("get")) {
                if (method.getParameterTypes().length != 0) {
                    throw invalidMethod(type, methodName, "getter methods cannot take parameters");
                }

                Class<?> returnType = method.getReturnType();
                if (!returnType.equals(String.class)) {
                    throw invalidMethod(type, methodName, "only String properties are supported");
                }

                // hardcoded for now
                ModelType<String> propertyType = ModelType.of(String.class);

                Character getterPropertyNameFirstChar = methodName.charAt(3);
                if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                    throw invalidMethod(type, methodName, "the 4th character of the getter method name must be an uppercase character");
                }

                String propertyNameCapitalized = methodName.substring(3);
                String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
                String setterName = "set" + propertyNameCapitalized;

                if (!methods.containsKey(setterName)) {
                    throw invalidMethod(type, methodName, "no corresponding setter for getter");
                }

                Method setter = methods.get(setterName);
                handled.add(setterName);

                if (!setter.getReturnType().equals(void.class)) {
                    throw invalidMethod(type, setterName, "setter method must have void return type");
                }

                Type[] setterParameterTypes = setter.getGenericParameterTypes();
                if (setterParameterTypes.length != 1) {
                    throw invalidMethod(type, setterName, "setter method must have exactly one parameter");
                }

                ModelType<?> setterType = ModelType.of(setterParameterTypes[0]);
                if (!setterType.equals(propertyType)) {
                    throw invalidMethod(type, setterName, "setter method param must be of exactly the same type as the getter returns (expected: " + propertyType + ", found: " + setterType + ")");
                }

                properties.add(new ModelProperty<String>(propertyName, propertyType));
                iterator.remove();
            }
        }

        methodNames.removeAll(handled);

        // TODO - should call out valid getters without setters
        if (!methodNames.isEmpty()) {
            throw invalid(type, "only paired getter/setter methods are supported (invalid methods: [" + Joiner.on(", ").join(methodNames) + "])");
        }

        return new ModelSchema<T>(type, properties);
    }

    public <T> void validateType(Class<T> type) {
        if (!type.isInterface()) {
            throw invalid(type, "must be defined as an interface");
        }

        if (!type.isAnnotationPresent(Managed.class)) {
            throw invalid(type, String.format("must be annotated with %s", Managed.class.getName()));
        }

        if (type.getInterfaces().length != 0) {
            throw invalid(type, "cannot extend other types");
        }

        if (type.getTypeParameters().length != 0) {
            throw invalid(type, "cannot be a parameterized type");
        }
    }

    public <T> InvalidManagedModelElementTypeException invalidMethod(Class<T> type, String methodName, String message) {
        return new InvalidManagedModelElementTypeException(type, message + " (method: " + methodName + ")");
    }

    public <T> InvalidManagedModelElementTypeException invalid(Class<T> type, String message) {
        return new InvalidManagedModelElementTypeException(type, message);
    }

}
