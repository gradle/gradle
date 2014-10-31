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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.model.Managed;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.state.ManagedInstance;
import org.gradle.model.internal.manage.state.ManagedModelElement;
import org.gradle.model.internal.manage.state.ModelPropertyInstance;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ThreadSafe
public class ManagedTypeModelSchemaExtractionHandler<T> implements ModelSchemaExtractionHandler<T> {

    private final ModelType<T> type;

    private final Spec<ModelType<?>> spec = new Spec<ModelType<?>>() {
        public boolean isSatisfiedBy(ModelType<?> element) {
            return isManaged(element);
        }
    };

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

    public ModelType<T> getType() {
        return type;
    }

    public Spec<? super ModelType<?>> getSpec() {
        return spec;
    }

    private ManagedTypeModelSchemaExtractionHandler(ModelType<T> type) {
        this.type = type;
    }

    public static ManagedTypeModelSchemaExtractionHandler<Object> getInstance() {
        return new ManagedTypeModelSchemaExtractionHandler<Object>(ModelType.of(Object.class));
    }

    public <R extends T> ModelSchemaExtractionResult<R> extract(final ModelType<R> type, ModelSchemaCache cache, final ModelSchemaExtractionContext context) {
        validateType(type);

        List<Method> methodList = Arrays.asList(type.getRawClass().getDeclaredMethods());
        if (methodList.isEmpty()) {
            ManagedTypeInstantiator<R> elementInstantiator = new ManagedTypeInstantiator<R>();
            ModelSchema<R> schema = new ModelSchema<R>(type, elementInstantiator);
            elementInstantiator.setSchema(schema);
            return new ModelSchemaExtractionResult<R>(schema);
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
                if (isManaged(returnType)) {
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
            throw new InvalidManagedModelElementTypeException(type, "only paired getter/setter methods are supported (invalid methods: [" + Joiner.on(", ").join(methodNames) + "])");
        }

        ManagedTypeInstantiator<R> elementInstantiator = new ManagedTypeInstantiator<R>();
        ModelSchema<R> schema = new ModelSchema<R>(type, properties, elementInstantiator);
        elementInstantiator.setSchema(schema);
        Iterable<? extends ModelSchemaExtractionContext> dependencies = getModelSchemaDependencies(properties, type, context);
        return new ModelSchemaExtractionResult<R>(schema, dependencies);
    }

    private <R extends T> Iterable<? extends ModelSchemaExtractionContext> getModelSchemaDependencies(Iterable<ModelProperty<?>> properties, final ModelType<R> type,
                                                                                                      final ModelSchemaExtractionContext context) {
        Iterable<ModelProperty<?>> managedProperties = Iterables.filter(properties, new Predicate<ModelProperty<?>>() {
            public boolean apply(ModelProperty<?> input) {
                return input.isManaged();
            }
        });
        return Iterables.transform(managedProperties, new Function<ModelProperty<?>, ModelSchemaExtractionContext>() {
            public ModelSchemaExtractionContext apply(ModelProperty<?> property) {
                return new PropertyExtractionContext(type, property, context);
            }
        });
    }

    private <P> ModelProperty<P> extractPropertyOfManagedType(final ModelSchemaCache schemaCache, ModelType<?> type, Map<String, Method> methods, String getterName, final ModelType<P> propertyType,
                                                              List<String> handled) {
        String propertyNameCapitalized = getterName.substring(3);
        String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
        String setterName = "set" + propertyNameCapitalized;

        if (methods.containsKey(setterName)) {
            validateSetter(type, propertyType, methods.get(setterName));
            handled.add(setterName);
            return new ModelProperty<P>(propertyName, propertyType, true);
        } else {
            return new ModelProperty<P>(propertyName, propertyType, true, new Factory<P>() {
                public P create() {
                    ModelSchema<P> modelSchema = schemaCache.get(propertyType);
                    return modelSchema.createInstance();
                }
            });

        }
    }

    private <P> ModelProperty<P> extractPropertyOfUnmanagedType(ModelType<?> type, Map<String, Method> methods, String getterName, final ModelType<P> propertyType, List<String> handled) {
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
        return new ModelProperty<P>(propertyName, propertyType);
    }

    private void validateSetter(ModelType<?> type, ModelType<?> propertyType, Method setter) {
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

    private boolean isSupportedUnmanagedType(final ModelType<?> propertyType) {
        return Iterables.any(SUPPORTED_UNMANAGED_TYPES, new Predicate<ModelType<?>>() {
            public boolean apply(ModelType<?> supportedType) {
                return supportedType.equals(propertyType);
            }
        });
    }

    public void validateType(ModelType<?> type) {
        Class<?> typeClass = type.getConcreteClass();

        if (!typeClass.isInterface()) {
            throw new InvalidManagedModelElementTypeException(type, "must be defined as an interface");
        }

        if (typeClass.getInterfaces().length > 0) {
            throw new InvalidManagedModelElementTypeException(type, "cannot extend other types");
        }

        if (typeClass.getTypeParameters().length > 0) {
            throw new InvalidManagedModelElementTypeException(type, "cannot be a parameterized type");
        }
    }

    private static boolean isManaged(ModelType<?> type) {
        return type.getRawClass().isAnnotationPresent(Managed.class);
    }

    private static class ManagedTypeInstantiator<T> implements Factory<T> {

        private ModelSchema<T> schema;

        public void setSchema(ModelSchema<T> schema) {
            this.schema = schema;
        }

        public T create() {
            Class<T> concreteType = schema.getType().getConcreteClass();
            ManagedModelElement<T> element = new ManagedModelElement<T>(schema);
            ManagedModelElementInvocationHandler invocationHandler = new ManagedModelElementInvocationHandler(element);
            return Cast.uncheckedCast(Proxy.newProxyInstance(concreteType.getClassLoader(), new Class<?>[]{concreteType, ManagedInstance.class}, invocationHandler));
        }
    }

    private static class ManagedModelElementInvocationHandler implements InvocationHandler {

        private final ManagedModelElement<?> element;

        private ManagedModelElementInvocationHandler(ManagedModelElement<?> element) {
            this.element = element;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            String propertyName = StringUtils.uncapitalize(methodName.substring(3));
            if (methodName.startsWith("get")) {
                return getInstanceProperty(ModelType.of(method.getGenericReturnType()), propertyName);
            } else if (methodName.startsWith("set")) {
                setInstanceProperty(ModelType.of(method.getGenericParameterTypes()[0]), propertyName, args[0]);
                return null;
            } else if (methodName.equals("hashCode")) {
                return hashCode();
            }
            throw new Exception("Unexpected method called: " + methodName);
        }

        private <U> void setInstanceProperty(ModelType<U> propertyType, String propertyName, Object value) {
            ModelPropertyInstance<U> modelPropertyInstance = element.get(propertyType, propertyName);
            if (modelPropertyInstance.getMeta().isManaged() && !ManagedInstance.class.isInstance(value)) {
                throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", propertyName, element.getType()));
            }
            modelPropertyInstance.set(Cast.cast(propertyType.getConcreteClass(), value));
        }

        private <U> U getInstanceProperty(ModelType<U> propertyType, String propertyName) {
            return element.get(propertyType, propertyName).get();
        }
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelType<?> type, String methodName, String message) {
        return new InvalidManagedModelElementTypeException(type, message + " (method: " + methodName + ")");
    }
}
