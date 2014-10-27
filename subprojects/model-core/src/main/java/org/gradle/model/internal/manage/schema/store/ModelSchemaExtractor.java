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

package org.gradle.model.internal.manage.schema.store;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang.StringUtils;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.ModelProperty;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@ThreadSafe
public class ModelSchemaExtractor {

    public final static List<? extends ModelType<?>> SUPPORTED_UNMANAGED_TYPES = ImmutableList.of(ModelType.of(String.class), ModelType.of(Boolean.class), ModelType.of(Integer.class),
            ModelType.of(Long.class), ModelType.of(Double.class), ModelType.of(BigInteger.class), ModelType.of(BigDecimal.class));

    public <T> ExtractedModelSchema<T> extract(ModelType<T> type) {
        validateType(type);

        List<Method> methodList = Arrays.asList(type.getRawClass().getDeclaredMethods());
        if (methodList.isEmpty()) {
            return new ExtractedModelSchema<T>(type, Collections.<ModelPropertyFactory<?>>emptyList());
        }

        List<ModelPropertyFactory<?>> propertyFactories = Lists.newLinkedList();

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


                Character getterPropertyNameFirstChar = methodName.charAt(3);
                if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                    throw invalidMethod(type, methodName, "the 4th character of the getter method name must be an uppercase character");
                }

                ModelType<?> returnType = ModelType.of(method.getGenericReturnType());
                if (isManaged(returnType.getRawClass())) {
                    propertyFactories.add(extractManagedProperty(type, methods, methodName, returnType, handled));
                } else {
                    propertyFactories.add(extractNonManagedProperty(type, methods, methodName, returnType, handled));
                }
                iterator.remove();
            }
        }

        methodNames.removeAll(handled);

        // TODO - should call out valid getters without setters
        if (!methodNames.isEmpty()) {
            throw invalid(type, "only paired getter/setter methods are supported (invalid methods: [" + Joiner.on(", ").join(methodNames) + "])");
        }

        return new ExtractedModelSchema<T>(type, propertyFactories);
    }

    private boolean isSupportedUnmanagedType(final ModelType<?> propertyType) {
        return Iterables.any(SUPPORTED_UNMANAGED_TYPES, new Predicate<ModelType<?>>() {
            public boolean apply(ModelType<?> supportedType) {
                return supportedType.equals(propertyType);
            }
        });
    }

    private <T> ModelPropertyFactory<T> extractNonManagedProperty(ModelType<?> type, Map<String, Method> methods, String getterName, final ModelType<T> propertyType, List<String> handled) {
        if (!isSupportedUnmanagedType(propertyType)) {
            String supportedTypes = Joiner.on(", ").join(SUPPORTED_UNMANAGED_TYPES);
            throw invalidMethod(type, getterName, String.format("%s is not a supported unmanaged property type, only the following types are supported: %s", propertyType, supportedTypes));
        }

        String propertyNameCapitalized = getterName.substring(3);
        final String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (!methods.containsKey(setterName)) {
            throw invalidMethod(type, getterName, "no corresponding setter for getter");
        }

        validateSetter(type, propertyType, methods.get(setterName));
        handled.add(setterName);
        return new ModelPropertyFactory<T>() {
            public ModelProperty<T> create(ModelSchemaStore store) {
                return new ModelProperty<T>(propertyName, propertyType);
            }
        };
    }

    private <T> void validateSetter(ModelType<?> type, ModelType<T> propertyType, Method setter) {
        if (!setter.getReturnType().equals(void.class)) {
            throw invalidMethod(type, setter.getName(), "setter method must have void return type");
        }

        Type[] setterParameterTypes = setter.getGenericParameterTypes();
        if (setterParameterTypes.length != 1) {
            throw invalidMethod(type, setter.getName(), "setter method must have exactly one parameter");
        }

        ModelType<?> setterType = ModelType.of(setterParameterTypes[0]);
        if (!setterType.equals(propertyType)) {
            throw invalidMethod(type, setter.getName(), "setter method param must be of exactly the same type as the getter returns (expected: " + propertyType + ", found: " + setterType + ")");
        }
    }

    private <T> ModelPropertyFactory<T> extractManagedProperty(ModelType<?> type, Map<String, Method> methods, String getterName, ModelType<T> propertyType,
                                                               List<String> handled) {
        String propertyNameCapitalized = getterName.substring(3);
        String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (methods.containsKey(setterName)) {
            validateSetter(type, propertyType, methods.get(setterName));
            handled.add(setterName);
            return new ManagedModelReferencePropertyFactory<T>(type, propertyType, propertyName);
        } else {
            return new ManagedModelInstancePropertyFactory<T>(type, propertyType, propertyName);
        }
    }

    public <T> void validateType(ModelType<T> type) {
        Class<T> typeClass = type.getConcreteClass();
        if (!isManaged(typeClass)) {
            throw invalid(type, String.format("must be annotated with %s", Managed.class.getName()));
        }

        if (!typeClass.isInterface()) {
            throw invalid(type, "must be defined as an interface");
        }

        if (typeClass.getInterfaces().length > 0) {
            throw invalid(type, "cannot extend other types");
        }

        if (typeClass.getTypeParameters().length > 0) {
            throw invalid(type, "cannot be a parameterized type");
        }
    }

    public boolean isManaged(Class<?> type) {
        return type.isAnnotationPresent(Managed.class);
    }

    public <T> InvalidManagedModelElementTypeException invalidMethod(ModelType<T> type, String methodName, String message) {
        return new InvalidManagedModelElementTypeException(type, message + " (method: " + methodName + ")");
    }

    public <T> InvalidManagedModelElementTypeException invalid(ModelType<T> type, String message) {
        return new InvalidManagedModelElementTypeException(type, message);
    }

}
