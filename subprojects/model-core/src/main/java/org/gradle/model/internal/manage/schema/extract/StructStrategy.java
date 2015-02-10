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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.*;
import com.google.common.collect.*;
import groovy.lang.GroovyObject;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.MethodDescription;
import org.gradle.internal.reflect.MethodSignatureEquivalence;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class StructStrategy implements ModelSchemaExtractionStrategy {

    private static final NoOpModelElementState NO_OP_MODEL_ELEMENT_STATE = new NoOpModelElementState();

    private final Set<Equivalence.Wrapper<Method>> ignoredMethods;

    private final Factory<String> supportedTypeDescriptions;
    private final MethodSignatureEquivalence equivalence = new MethodSignatureEquivalence();

    private final ManagedProxyClassGenerator classGenerator = new ManagedProxyClassGenerator();
    private final ManagedProxyFactory proxyFactory = new ManagedProxyFactory();

    public StructStrategy(Factory<String> supportedTypeDescriptions) {
        this.supportedTypeDescriptions = supportedTypeDescriptions;

        Iterable<Method> ignoredMethods = Iterables.concat(Arrays.asList(Object.class.getMethods()), Arrays.asList(GroovyObject.class.getMethods()));
        this.ignoredMethods = ImmutableSet.copyOf(Iterables.transform(ignoredMethods, new Function<Method, Equivalence.Wrapper<Method>>() {
            public Equivalence.Wrapper<Method> apply(@Nullable Method input) {
                return equivalence.wrap(input);
            }
        }));
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton("interfaces and abstract classes annotated with " + Managed.class.getName());
    }

    public <R> ModelSchemaExtractionResult<R> extract(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        Class<? super R> clazz = type.getRawClass();
        if (clazz.isAnnotationPresent(Managed.class)) {
            validateType(type, extractionContext);

            Iterable<Method> methods = Arrays.asList(clazz.getMethods());
            if (!clazz.isInterface()) {
                methods = filterIgnoredMethods(methods);
            }
            ImmutableListMultimap<String, Method> methodsByName = Multimaps.index(methods, new Function<Method, String>() {
                public String apply(Method method) {
                    return method.getName();
                }
            });

            ensureNoOverloadedMethods(extractionContext, methodsByName);

            List<ModelProperty<?>> properties = Lists.newLinkedList();
            List<Method> handled = Lists.newArrayListWithCapacity(clazz.getMethods().length);
            ReturnTypeSpecializationOrdering returnTypeSpecializationOrdering = new ReturnTypeSpecializationOrdering();

            for (String methodName : methodsByName.keySet()) {
                if (methodName.startsWith("get") && !methodName.equals("get")) {
                    ImmutableList<Method> getterMethods = methodsByName.get(methodName);

                    // The overload check earlier verified that all methods for are equivalent for our purposes
                    // So, taking the first one with the most specialized return type is fine.
                    Method sampleMethod = returnTypeSpecializationOrdering.max(getterMethods);

                    boolean abstractGetter = Modifier.isAbstract(sampleMethod.getModifiers());

                    if (sampleMethod.getParameterTypes().length != 0) {
                        throw invalidMethod(extractionContext, "getter methods cannot take parameters", sampleMethod);
                    }

                    Character getterPropertyNameFirstChar = methodName.charAt(3);
                    if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                        throw invalidMethod(extractionContext, "the 4th character of the getter method name must be an uppercase character", sampleMethod);
                    }

                    ModelType<?> returnType = ModelType.returnType(sampleMethod);

                    String propertyNameCapitalized = methodName.substring(3);
                    String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
                    String setterName = "set" + propertyNameCapitalized;
                    ImmutableList<Method> setterMethods = methodsByName.get(setterName);

                    boolean isWritable = !setterMethods.isEmpty();
                    if (isWritable) {
                        Method setter = setterMethods.get(0);

                        if (!abstractGetter) {
                            throw invalidMethod(extractionContext, "setters are not allowed for non-abstract getters", setter);
                        }
                        validateSetter(extractionContext, returnType, setter);
                        handled.addAll(setterMethods);
                    }

                    if (abstractGetter) {
                        ImmutableSet<ModelType<?>> declaringClasses = ImmutableSet.copyOf(Iterables.transform(getterMethods, new Function<Method, ModelType<?>>() {
                            public ModelType<?> apply(Method input) {
                                return ModelType.of(input.getDeclaringClass());
                            }
                        }));

                        boolean unmanaged = Iterables.any(getterMethods, new Predicate<Method>() {
                            public boolean apply(Method input) {
                                return input.getAnnotation(Unmanaged.class) != null;
                            }
                        });

                        properties.add(ModelProperty.of(returnType, propertyName, isWritable, declaringClasses, unmanaged));
                    }
                    handled.addAll(getterMethods);
                }
            }

            Iterable<Method> notHandled = Iterables.filter(methodsByName.values(), Predicates.not(Predicates.in(handled)));

            // TODO - should call out valid getters without setters
            if (!Iterables.isEmpty(notHandled)) {
                throw invalidMethods(extractionContext, "only paired getter/setter methods are supported", notHandled);
            }

            Class<R> concreteClass = type.getConcreteClass();
            Class<? extends R> implClass = classGenerator.generate(concreteClass);
            final ModelStructSchema<R> schema = ModelSchema.struct(type, properties, implClass);
            extractionContext.addValidator(new Action<ModelSchemaExtractionContext<R>>() {
                @Override
                public void execute(ModelSchemaExtractionContext<R> validatorModelSchemaExtractionContext) {
                    ensureCanBeInstantiated(extractionContext, schema);
                }
            });
            Iterable<ModelSchemaExtractionContext<?>> propertyDependencies = Iterables.transform(properties, new Function<ModelProperty<?>, ModelSchemaExtractionContext<?>>() {
                public ModelSchemaExtractionContext<?> apply(final ModelProperty<?> property) {
                    return toPropertyExtractionContext(extractionContext, property, cache);
                }
            });

            return new ModelSchemaExtractionResult<R>(schema, propertyDependencies);
        } else {
            return null;
        }
    }

    private <R> void ensureCanBeInstantiated(ModelSchemaExtractionContext<R> extractionContext, ModelStructSchema<R> schema) {
        try {
            proxyFactory.createProxy(NO_OP_MODEL_ELEMENT_STATE, schema);
        } catch (Throwable e) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "instance creation failed", e);
        }
    }

    private Iterable<Method> filterIgnoredMethods(Iterable<Method> methods) {
        return Iterables.filter(methods, new Predicate<Method>() {
            @Override
            public boolean apply(Method method) {
                return !method.isSynthetic() && !ignoredMethods.contains(equivalence.wrap(method));
            }
        });
    }

    private <R> void ensureNoOverloadedMethods(ModelSchemaExtractionContext<R> extractionContext, final ImmutableListMultimap<String, Method> methodsByName) {
        ImmutableSet<String> methodNames = methodsByName.keySet();
        for (String methodName : methodNames) {
            ImmutableList<Method> methods = methodsByName.get(methodName);
            if (methods.size() > 1) {
                List<Method> deduped = CollectionUtils.dedup(methods, equivalence);
                if (deduped.size() > 1) {
                    throw invalidMethods(extractionContext, "overloaded methods are not supported", deduped);
                }
            }
        }
    }

    private <R, P> ModelSchemaExtractionContext<P> toPropertyExtractionContext(final ModelSchemaExtractionContext<R> parentContext, final ModelProperty<P> property, final ModelSchemaCache modelSchemaCache) {
        return parentContext.child(property.getType(), propertyDescription(parentContext, property), new Action<ModelSchemaExtractionContext<P>>() {
            public void execute(ModelSchemaExtractionContext<P> propertyExtractionContext) {
                ModelSchema<P> propertySchema = modelSchemaCache.get(property.getType());

                if (propertySchema.getKind().isAllowedPropertyTypeOfManagedType() && property.isUnmanaged()) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "property '%s' is marked as @Unmanaged, but is of @Managed type '%s'. Please remove the @Managed annotation.%n%s",
                            property.getName(), property.getType(), supportedTypeDescriptions.create()
                    ));
                }

                if (!propertySchema.getKind().isAllowedPropertyTypeOfManagedType() && !property.isUnmanaged()) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "type %s cannot be used for property '%s' as it is an unmanaged type (please annotate the getter with @org.gradle.model.Unmanaged if you want this property to be unmanaged).%n%s",
                            property.getType(), property.getName(), supportedTypeDescriptions.create()
                    ));
                }

                if (!property.isWritable()) {
                    if (property.isUnmanaged()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                                "unmanaged property '%s' cannot be read only, unmanaged properties must have setters",
                                property.getName())
                        );
                    }

                    if (!propertySchema.getKind().isManaged()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                                "read only property '%s' has non managed type %s, only managed types can be used",
                                property.getName(), property.getType()));
                    }
                }
            }
        });
    }

    private String propertyDescription(ModelSchemaExtractionContext<?> parentContext, ModelProperty<?> property) {
        if (property.getDeclaredBy().size() == 1 && property.getDeclaredBy().contains(parentContext.getType())) {
            return String.format("property '%s'", property.getName());
        } else {
            ImmutableSortedSet<String> declaredBy = ImmutableSortedSet.copyOf(Iterables.transform(property.getDeclaredBy(), Functions.toStringFunction()));
            return String.format("property '%s' declared by %s", property.getName(), Joiner.on(", ").join(declaredBy));
        }
    }

    private void validateSetter(ModelSchemaExtractionContext<?> extractionContext, ModelType<?> propertyType, Method setter) {
        if (!Modifier.isAbstract(setter.getModifiers())) {
            throw invalidMethod(extractionContext, "non-abstract setters are not allowed", setter);
        }

        if (!setter.getReturnType().equals(void.class)) {
            throw invalidMethod(extractionContext, "setter method must have void return type", setter);
        }

        Type[] setterParameterTypes = setter.getGenericParameterTypes();
        if (setterParameterTypes.length != 1) {
            throw invalidMethod(extractionContext, "setter method must have exactly one parameter", setter);
        }

        ModelType<?> setterType = ModelType.paramType(setter, 0);
        if (!setterType.equals(propertyType)) {
            String message = "setter method param must be of exactly the same type as the getter returns (expected: " + propertyType + ", found: " + setterType + ")";
            throw invalidMethod(extractionContext, message, setter);
        }
    }

    private void validateType(ModelType<?> type, ModelSchemaExtractionContext<?> extractionContext) {
        Class<?> typeClass = type.getConcreteClass();

        if (!typeClass.isInterface() && !Modifier.isAbstract(typeClass.getModifiers())) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "must be defined as an interface or an abstract class.");
        }

        if (typeClass.getTypeParameters().length > 0) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "cannot be a parameterized type.");
        }

        Constructor<?> customConstructor = findCustomConstructor(typeClass);
        if (customConstructor != null) {
            throw invalidMethod(extractionContext, "custom constructors are not allowed", customConstructor);
        }

        ensureNoInstanceScopedFields(extractionContext, typeClass);
        ensureNoProtectedOrPrivateMethods(extractionContext, typeClass);
    }

    private void ensureNoProtectedOrPrivateMethods(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        Class<?> superClass = typeClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            ensureNoProtectedOrPrivateMethods(extractionContext, superClass);
        }

        Iterable<Method> protectedAndPrivateMethods = Iterables.filter(Arrays.asList(typeClass.getDeclaredMethods()), new Predicate<Method>() {
            @Override
            public boolean apply(Method method) {
                int modifiers = method.getModifiers();
                return !method.isSynthetic() && (Modifier.isProtected(modifiers) || Modifier.isPrivate(modifiers));
            }
        });

        if (!Iterables.isEmpty(protectedAndPrivateMethods)) {
            throw invalidMethods(extractionContext, "protected and private methods are not allowed", protectedAndPrivateMethods);
        }
    }

    private void ensureNoInstanceScopedFields(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        Class<?> superClass = typeClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            ensureNoInstanceScopedFields(extractionContext, superClass);
        }

        List<Field> declaredFields = Arrays.asList(typeClass.getDeclaredFields());
        Iterable<Field> instanceScopedFields = Iterables.filter(declaredFields, new Predicate<Field>() {
            public boolean apply(Field field) {
                return !Modifier.isStatic(field.getModifiers()) && !field.getName().equals("metaClass");
            }
        });
        ImmutableSortedSet<String> sortedDescriptions = ImmutableSortedSet.copyOf(Iterables.transform(instanceScopedFields, new Function<Field, String>() {
            public String apply(Field field) {
                return field.toString();
            }
        }));
        if (!sortedDescriptions.isEmpty()) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "instance scoped fields are not allowed (found fields: " + Joiner.on(", ").join(sortedDescriptions) + ").");
        }
    }

    private Constructor<?> findCustomConstructor(Class<?> typeClass) {
        Class<?> superClass = typeClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            Constructor<?> customSuperConstructor = findCustomConstructor(typeClass.getSuperclass());
            if (customSuperConstructor != null) {
                return customSuperConstructor;
            }
        }
        Constructor<?>[] constructors = typeClass.getConstructors();
        if (constructors.length == 0 || (constructors.length == 1 && constructors[0].getParameterTypes().length == 0)) {
            return null;
        } else {
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterTypes().length > 0) {
                    return constructor;
                }
            }
            //this should never happen
            throw new RuntimeException(String.format("Expected a constructor taking at least one argument in %s but no such constructors were found", typeClass.getName()));
        }
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelSchemaExtractionContext<?> extractionContext, String message, Method method) {
        return invalidMethod(extractionContext, message, MethodDescription.of(method));
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelSchemaExtractionContext<?> extractionContext, String message, Constructor<?> constructor) {
        return invalidMethod(extractionContext, message, MethodDescription.of(constructor));
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelSchemaExtractionContext<?> extractionContext, String message, MethodDescription methodDescription) {
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (invalid method: " + methodDescription.toString() + ").");
    }

    private InvalidManagedModelElementTypeException invalidMethods(ModelSchemaExtractionContext<?> extractionContext, String message, final Iterable<Method> methods) {
        final ImmutableSortedSet<String> descriptions = ImmutableSortedSet.copyOf(Iterables.transform(methods, new Function<Method, String>() {
            public String apply(Method method) {
                return MethodDescription.of(method).toString();
            }
        }));
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (invalid methods: " + Joiner.on(", ").join(descriptions) + ").");
    }

    static private class ReturnTypeSpecializationOrdering extends Ordering<Method> {

        @Override
        public int compare(Method left, Method right) {
            Class<?> leftType = left.getReturnType();
            Class<?> rightType = right.getReturnType();
            if (leftType.equals(rightType)) {
                return 0;
            }
            if (leftType.isAssignableFrom(rightType)) {
                return -1;
            }
            if (rightType.isAssignableFrom(leftType)) {
                return 1;
            }
            throw new UnsupportedOperationException(String.format("Cannot compare two types that aren't part of an inheritance hierarchy: %s, %s", leftType, rightType));
        }
    }

    private static class NoOpModelElementState implements ModelElementState {
        @Override
        public MutableModelNode getBackingNode() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        public Object get(String name) {
            return null;
        }

        public void set(String name, Object value) {
        }
    }
}
