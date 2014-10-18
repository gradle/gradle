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

package org.gradle.model.internal.manage.schema.extraction;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Factory;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.state.ManagedModelElement;
import org.gradle.model.internal.manage.state.ManagedModelElementInstanceFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

public class ModelSchemaExtractor {

    public final ModelSchemaStore store;
    private final ManagedModelElementInstanceFactory managedElementFactory;

    public ModelSchemaExtractor(ModelSchemaStore store, ManagedModelElementInstanceFactory managedElementFactory) {
        this.store = store;
        this.managedElementFactory = managedElementFactory;
    }

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


                Character getterPropertyNameFirstChar = methodName.charAt(3);
                if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                    throw invalidMethod(type, methodName, "the 4th character of the getter method name must be an uppercase character");
                }

                Class<?> returnType = method.getReturnType();
                ModelType<?> returnModelType = ModelType.of(returnType);
                if (returnType.equals(String.class)) {
                    properties.add(extractNonManagedProperty(type, methods, methodName, returnModelType, handled));
                } else if (isManaged(returnType)) {
                    properties.add(extractManagedProperty(type, methods, methodName, returnModelType, handled));
                } else {
                    throw invalidMethod(type, methodName, "only String and managed properties are supported");
                }
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

    private <T> ModelProperty<T> extractNonManagedProperty(Class<?> type, Map<String, Method> methods, String getterName, ModelType<T> propertyType, List<String> handled) {
        String propertyNameCapitalized = getterName.substring(3);
        String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (!methods.containsKey(setterName)) {
            throw invalidMethod(type, getterName, "no corresponding setter for getter");
        }

        validateSetter(type, propertyType, methods.get(setterName));
        handled.add(setterName);
        return new ModelProperty<T>(propertyName, propertyType);
    }

    private <T> void validateSetter(Class<?> type, ModelType<T> propertyType, Method setter) {
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

    private <T> ModelProperty<T> extractManagedProperty(Class<?> type, Map<String, Method> methods, String getterName, ModelType<T> propertyType, List<String> handled) {
        String propertyNameCapitalized = getterName.substring(3);
        String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (methods.containsKey(setterName)) {
            validateSetter(type, propertyType, methods.get(setterName));
            handled.add(setterName);
            getModelSchema(type, propertyType.getConcreteClass(), propertyName);
            return new ModelProperty<T>(propertyName, propertyType, true);
        } else {
            final ModelSchema<T> modelSchema = getModelSchema(type, propertyType.getConcreteClass(), propertyName);
            return new ModelProperty<T>(propertyName, propertyType, true, new Factory<T>() {
                public T create() {
                    ManagedModelElement<T> managedModelElement = new ManagedModelElement<T>(modelSchema);
                    return managedElementFactory.create(managedModelElement);
                }
            });
        }
    }

    private <T> ModelSchema<T> getModelSchema(Class<?> type, Class<T> propertyType, String propertyName) {
        try {
            return store.getSchema(propertyType);
        } catch (InvalidManagedModelElementTypeException e) {
            throw new InvalidManagedModelElementTypeException(type, propertyName, e);
        }
    }

    public <T> void validateType(Class<T> type) {
        if (!isManaged(type)) {
            throw invalid(type, String.format("must be annotated with %s", Managed.class.getName()));
        }

        if (!type.isInterface()) {
            throw invalid(type, "must be defined as an interface");
        }

        if (type.getInterfaces().length != 0) {
            throw invalid(type, "cannot extend other types");
        }

        if (type.getTypeParameters().length != 0) {
            throw invalid(type, "cannot be a parameterized type");
        }
    }

    public boolean isManaged(Class<?> type) {
        return type.isAnnotationPresent(Managed.class);
    }

    public <T> InvalidManagedModelElementTypeException invalidMethod(Class<T> type, String methodName, String message) {
        return new InvalidManagedModelElementTypeException(type, message + " (method: " + methodName + ")");
    }

    public <T> InvalidManagedModelElementTypeException invalid(Class<T> type, String message) {
        return new InvalidManagedModelElementTypeException(type, message);
    }

}
