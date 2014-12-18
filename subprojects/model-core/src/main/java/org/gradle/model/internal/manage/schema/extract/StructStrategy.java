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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StructStrategy implements ModelSchemaExtractionStrategy {
    protected final Factory<String> supportedTypeDescriptions;
    protected final ModelSchemaExtractor extractor;

    public StructStrategy(ModelSchemaExtractor extractor, Factory<String> supportedTypeDescriptions) {
        this.supportedTypeDescriptions = supportedTypeDescriptions;
        this.extractor = extractor;
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton("interfaces annotated with " + Managed.class.getName());
    }

    public <R> ModelSchemaExtractionResult<R> extract(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        Class<? super R> clazz = type.getRawClass();
        if (clazz.isAnnotationPresent(Managed.class)) {
            validateType(type, extractionContext);

            ImmutableListMultimap<String, Method> methodsByName = Multimaps.index(Arrays.asList(clazz.getMethods()), new Function<Method, String>() {
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
                        validateSetter(extractionContext, returnType, setterMethods.get(0));
                        handled.addAll(setterMethods);
                    }

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
                    handled.addAll(getterMethods);
                }
            }

            Iterable<Method> notHandled = Iterables.filter(methodsByName.values(), Predicates.not(Predicates.in(handled)));

            // TODO - should call out valid getters without setters
            if (!Iterables.isEmpty(notHandled)) {
                throw invalidMethods(extractionContext, "only paired getter/setter methods are supported", notHandled);
            }

            ModelSchema<R> schema = ModelSchema.struct(type, properties);
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

    private <R> void ensureNoOverloadedMethods(ModelSchemaExtractionContext<R> extractionContext, final ImmutableListMultimap<String, Method> methodsByName) {
        ImmutableSet<String> methodNames = methodsByName.keySet();
        for (String methodName : methodNames) {
            ImmutableList<Method> methods = methodsByName.get(methodName);
            if (methods.size() > 1) {
                List<Method> deduped = CollectionUtils.dedup(methods, new MethodSignatureEquivalence());
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
                            "property '%s' is marked as @Unmanaged, but is of @Managed type '%s'. Please remote the @Managed annotation.%n%s",
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

        if (!typeClass.isInterface()) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "must be defined as an interface.");
        }

        if (typeClass.getTypeParameters().length > 0) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "cannot be a parameterized type.");
        }
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelSchemaExtractionContext<?> extractionContext, String message, Method method) {
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (invalid method: " + description(method) + ").");
    }

    private InvalidManagedModelElementTypeException invalidMethods(ModelSchemaExtractionContext<?> extractionContext, String message, final Iterable<Method> methods) {
        final ImmutableSortedSet<String> descriptions = ImmutableSortedSet.copyOf(Iterables.transform(methods, new Function<Method, String>() {
            public String apply(Method method) {
                return description(method).toString();
            }
        }));
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (invalid methods: " + Joiner.on(", ").join(descriptions) + ").");
    }

    private MethodDescription description(Method method) {
        return MethodDescription.name(method.getName())
                .owner(method.getDeclaringClass())
                .returns(method.getGenericReturnType())
                .takes(method.getGenericParameterTypes())
                .build();
    }

    static private class MethodSignatureEquivalence extends Equivalence<Method> {

        @Override
        protected boolean doEquivalent(Method a, Method b) {
            boolean equals = new EqualsBuilder()
                    .append(a.getName(), b.getName())
                    .append(a.getGenericParameterTypes(), b.getGenericParameterTypes())
                    .isEquals();
            if (equals) {
                equals = a.getReturnType().equals(b.getReturnType())
                        || a.getReturnType().isAssignableFrom(b.getReturnType())
                        || b.getReturnType().isAssignableFrom(a.getReturnType());
            }
            return equals;
        }

        @Override
        protected int doHash(Method method) {
            return new HashCodeBuilder()
                    .append(method.getName())
                    .append(method.getGenericParameterTypes())
                    .toHashCode();
        }
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
}
