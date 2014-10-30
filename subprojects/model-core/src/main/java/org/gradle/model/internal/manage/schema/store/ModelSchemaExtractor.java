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
import com.google.common.collect.*;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.model.Managed;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.state.ManagedModelElement;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@ThreadSafe
public class ModelSchemaExtractor {

    public final static List<? extends ModelType<?>> SUPPORTED_UNMANAGED_TYPES = ImmutableList.of(ModelType.of(String.class), ModelType.of(Boolean.class), ModelType.of(Integer.class),
            ModelType.of(Long.class), ModelType.of(Double.class), ModelType.of(BigInteger.class), ModelType.of(BigDecimal.class));

    private final static Map<ModelType<?>, Class<?>> BOXED_REPLACEMENTS = ImmutableMap.<ModelType<?>, Class<?>>builder()
            .put(ModelType.of(Boolean.TYPE), Boolean.class)
            .put(ModelType.of(Character.TYPE), Integer.class)
            .put(ModelType.of(Float.TYPE), Double.class)
            .put(ModelType.of(Integer.TYPE), Integer.class)
            .put(ModelType.of(Long.TYPE), Long.class)
            .put(ModelType.of(Short.TYPE), Integer.class)
            .put(ModelType.of(Double.TYPE), Double.class)
            .build();

    private static class WorkingSet<T> {
        List<T> items;
        int i = -1;

        private WorkingSet(List<T> items) {
            this.items = items;
        }

        T current() {
            return i < items.size() ? items.get(Math.max(i, 0)) : null;
        }

        T next() {
            ++i;
            return current();
        }

        boolean hasNext() {
            return i < items.size() - 1;
        }
    }

    private static class Extraction {
        final ModelType<?> root;
        final Deque<WorkingSet<ModelProperty<?>>> stack = Lists.newLinkedList();

        private Extraction(ModelType<?> root) {
            this.root = root;
        }

        void push(List<ModelProperty<?>> items) {
            if (!items.isEmpty()) {
                stack.push(new WorkingSet<ModelProperty<?>>(items));
            }
        }

        ModelProperty<?> next() {
            if (stack.isEmpty()) {
                return null;
            } else {
                WorkingSet<ModelProperty<?>> childSet = stack.peek();
                if (childSet.hasNext()) {
                    return childSet.next();
                } else {
                    stack.pop();
                    return next();
                }
            }
        }

        List<ModelProperty<?>> getCurrentStack() {
            List<ModelProperty<?>> currentStack = Lists.newLinkedList();
            for (WorkingSet<ModelProperty<?>> set : stack) {
                currentStack.add(set.current());
            }
            return currentStack;
        }
    }

    public <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaCache cache) {
        ModelSchema<T> schema = extractSchema(type, cache);

        Extraction extraction = new Extraction(type);
        pushDependencies(schema, extraction, cache);
        ModelProperty<?> next = extraction.next();

        while (next != null) {
            ModelSchema<?> nextSchema;
            try {
                nextSchema = extractSchema(next.getType(), cache);
            } catch (InvalidManagedModelElementTypeException e) {
                InvalidManagedModelElementTypeException cause = e;
                List<ModelProperty<?>> currentStack = extraction.getCurrentStack();
                while (!currentStack.isEmpty()) {
                    ModelProperty<?> previous = currentStack.remove(0);
                    ModelType<?> owner = currentStack.isEmpty() ? type : currentStack.get(0).getType();
                    cause = invalid(owner, String.format("managed type of property '%s' is invalid", previous.getName()), cause);
                }

                throw cause;
            }

            pushDependencies(nextSchema, extraction, cache);
            next = extraction.next();
        }

        return schema;
    }

    private <T> void pushDependencies(ModelSchema<T> schema, Extraction extraction, final ModelSchemaCache cache) {
        cache.set(schema.getType(), schema);
        Iterable<ModelProperty<?>> pendingProperties = Iterables.filter(schema.getProperties().values(), new Predicate<ModelProperty<?>>() {
            public boolean apply(ModelProperty<?> input) {
                return input.isManaged() && cache.get(input.getType()) == null;
            }
        });

        extraction.push(Lists.newLinkedList(pendingProperties));
    }

    private <T> ModelSchema<T> extractSchema(ModelType<T> type, ModelSchemaCache cache) {
        ModelSchema<T> cached = cache.get(type);
        if (cached != null) {
            return cached;
        }

        if (type.getRawClass().equals(ManagedSet.class)) {
            ModelType<ManagedSet<?>> managedSetModelType = Cast.uncheckedCast(type);
            return Cast.uncheckedCast(extractManagedSetSchema(managedSetModelType, cache));
        }

        validateType(type);

        List<Method> methodList = Arrays.asList(type.getRawClass().getDeclaredMethods());
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

        for (String methodName : methodNames) {
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
                    properties.add(extractPropertyOfManagedType(cache, type, methods, methodName, returnType, handled));
                } else {
                    properties.add(extractPropertyOfUnmanagedType(type, methods, methodName, returnType, handled));
                }
                handled.add(methodName);
            }
        }

        methodNames.removeAll(handled);

        // TODO - should call out valid getters without setters
        if (!methodNames.isEmpty()) {
            throw invalid(type, "only paired getter/setter methods are supported (invalid methods: [" + Joiner.on(", ").join(methodNames) + "])");
        }

        ModelSchema<T> schema = new ModelSchema<T>(type, properties);
        cache.set(type, schema);
        return schema;
    }

    private <T extends ManagedSet<?>> ModelSchema<T> extractManagedSetSchema(ModelType<T> type, ModelSchemaCache cache) {
        List<ModelType<?>> typeVariables = type.getTypeVariables();

        if (typeVariables.isEmpty()) {
            throw invalid(type, String.format("type parameter of %s has to be specified", ManagedSet.class.getName()));
        }
        if (type.isHasWildcardTypeVariables()) {
            throw invalid(type, String.format("type parameter of %s cannot be a wildcard", ManagedSet.class.getName()));
        }
        ModelType<?> typeParameter = typeVariables.get(0);
        if (!isManaged(typeParameter.getRawClass())) {
            throw invalid(type, String.format("type parameter of %s has to be a managed type", ManagedSet.class.getName()));
        }

        try {
            return new ModelSchema<T>(type, Collections.<ModelProperty<?>>emptyList(), ImmutableList.<ModelSchema<?>>of(extract(typeParameter, cache)));
        } catch (InvalidManagedModelElementTypeException e) {
            throw invalid(type, String.format("type parameter of %s has to be a valid managed type", ManagedSet.class.getName()), e);
        }
    }

    private boolean isSupportedUnmanagedType(final ModelType<?> propertyType) {
        return Iterables.any(SUPPORTED_UNMANAGED_TYPES, new Predicate<ModelType<?>>() {
            public boolean apply(ModelType<?> supportedType) {
                return supportedType.equals(propertyType);
            }
        });
    }

    private <T> ModelProperty<T> extractPropertyOfUnmanagedType(ModelType<?> type, Map<String, Method> methods, String getterName, final ModelType<T> propertyType, List<String> handled) {
        Class<?> boxedType = BOXED_REPLACEMENTS.get(propertyType);
        if (boxedType != null) {
            throw invalidMethod(type, getterName, String.format("%s is not a supported property type, use %s instead", propertyType, boxedType.getName()));
        }
        if (!isSupportedUnmanagedType(propertyType)) {
            String supportedTypes = Joiner.on(", ").join(SUPPORTED_UNMANAGED_TYPES);
            throw invalidMethod(type, getterName, String.format("%s is not a supported property type, only managed and the following unmanaged types are supported: %s", propertyType, supportedTypes));
        }

        String propertyNameCapitalized = getterName.substring(3);
        final String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (!methods.containsKey(setterName)) {
            throw invalidMethod(type, getterName, "no corresponding setter for getter");
        }

        validateSetter(type, propertyType, methods.get(setterName));
        handled.add(setterName);
        return new ModelProperty<T>(propertyName, propertyType);
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

    private <T> ModelProperty<T> extractPropertyOfManagedType(final ModelSchemaCache schemaCache, ModelType<?> type, Map<String, Method> methods, String getterName, final ModelType<T> propertyType,
                                                              List<String> handled) {
        String propertyNameCapitalized = getterName.substring(3);
        String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (methods.containsKey(setterName)) {
            validateSetter(type, propertyType, methods.get(setterName));
            handled.add(setterName);
            return new ModelProperty<T>(propertyName, propertyType, true);
        } else {
            return new ModelProperty<T>(propertyName, propertyType, true, new Factory<T>() {
                public T create() {
                    ModelSchema<T> modelSchema = schemaCache.get(propertyType);
                    return new ManagedModelElement<T>(modelSchema).createInstance();
                }
            });

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

    private <T> InvalidManagedModelElementTypeException invalid(ModelType<T> type, String message, InvalidManagedModelElementTypeException e) {
        return new InvalidManagedModelElementTypeException(type, message, e);
    }

}
