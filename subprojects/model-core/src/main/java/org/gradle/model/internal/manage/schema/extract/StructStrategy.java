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
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

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
        if (type.getRawClass().isAnnotationPresent(Managed.class)) {
            validateType(type, extractionContext);

            Iterable<Method> methods = removeEquivalentMethods(type.getRawClass().getMethods());
            if (Iterables.isEmpty(methods)) {
                return new ModelSchemaExtractionResult<R>(ModelSchema.struct(type, Collections.<ModelProperty<?>>emptySet()));
            }

            List<ModelProperty<?>> properties = Lists.newLinkedList();

            final ImmutableListMultimap<String, Method> methodsByName = Multimaps.index(methods, new Function<Method, String>() {
                public String apply(Method method) {
                    return method.getName();
                }
            });
            ensureNoOverloadedMethods(extractionContext, methodsByName);

            List<Method> handled = Lists.newArrayList();

            for (Method method : methods) {
                String methodName = method.getName();
                if (methodName.startsWith("get") && !methodName.equals("get")) {
                    if (method.getParameterTypes().length != 0) {
                        throw invalidMethod(extractionContext, "getter methods cannot take parameters", method);
                    }

                    Character getterPropertyNameFirstChar = methodName.charAt(3);
                    if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                        throw invalidMethod(extractionContext, "the 4th character of the getter method name must be an uppercase character", method);
                    }

                    ModelType<?> returnType = ModelType.of(method.getGenericReturnType());

                    String propertyNameCapitalized = methodName.substring(3);
                    String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
                    String setterName = "set" + propertyNameCapitalized;
                    boolean isWritable = methodsByName.containsKey(setterName);

                    if (isWritable) {
                        Method setter = methodsByName.get(setterName).get(0);
                        validateSetter(extractionContext, returnType, setter);
                        handled.add(setter);
                    }

                    properties.add(ModelProperty.of(returnType, propertyName, isWritable));
                    handled.add(method);
                }
            }

            Iterable<Method> notHandled = Iterables.filter(methods, Predicates.not(Predicates.in(handled)));

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
        Optional<String> overloadedMethodName = Iterables.tryFind(methodsByName.keySet(), new Predicate<String>() {
            public boolean apply(String methodName) {
                return methodsByName.get(methodName).size() > 1;
            }
        });

        if (overloadedMethodName.isPresent()) {
            throw invalidMethods(extractionContext, "overloaded methods are not supported", methodsByName.get(overloadedMethodName.get()));
        }
    }

    private Iterable<Method> removeEquivalentMethods(Method[] methods) {
        final MethodSignatureEquivalence equivalence = new MethodSignatureEquivalence();
        Iterable<Equivalence.Wrapper<Method>> methodEquivalenceWrappers = Iterables.transform(Arrays.asList(methods), new Function<Method, Equivalence.Wrapper<Method>>() {
            public Equivalence.Wrapper<Method> apply(Method method) {
                return equivalence.wrap(method);
            }
        });
        return Iterables.transform(ImmutableSet.copyOf(methodEquivalenceWrappers), new Function<Equivalence.Wrapper<Method>, Method>() {
            public Method apply(Equivalence.Wrapper<Method> wrapper) {
                return wrapper.get();
            }
        });
    }

    private <R, P> ModelSchemaExtractionContext<P> toPropertyExtractionContext(final ModelSchemaExtractionContext<R> parentContext, final ModelProperty<P> property, final ModelSchemaCache modelSchemaCache) {
        return parentContext.child(property.getType(), String.format("property '%s'", property.getName()), new Action<ModelSchemaExtractionContext<P>>() {
            public void execute(ModelSchemaExtractionContext<P> propertyExtractionContext) {
                ModelSchema<P> propertySchema = modelSchemaCache.get(property.getType());

                if (!propertySchema.getKind().isAllowedPropertyTypeOfManagedType()) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "type %s cannot be used for property '%s' as it is an unmanaged type.%n%s",
                            property.getType(), property.getName(), supportedTypeDescriptions.create()
                    ));
                }

                if (!property.isWritable()) {
                    if (!propertySchema.getKind().isManaged()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                                "read only property '%s' has non managed type %s, only managed types can be used",
                                property.getName(), property.getType()));
                    }
                }
            }
        });
    }

    private void validateSetter(ModelSchemaExtractionContext<?> extractionContext, ModelType<?> propertyType, Method setter) {
        if (!setter.getReturnType().equals(void.class)) {
            throw invalidMethod(extractionContext, "setter method must have void return type", setter);
        }

        Type[] setterParameterTypes = setter.getGenericParameterTypes();
        if (setterParameterTypes.length != 1) {
            throw invalidMethod(extractionContext, "setter method must have exactly one parameter", setter);
        }

        ModelType<?> setterType = ModelType.of(setterParameterTypes[0]);
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
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (invalid methods: " + Joiner.on(", ").join(descriptions)+ ").");
    }

    private MethodDescription description(Method method) {
        return MethodDescription.name(method.getName())
                .owner(method.getDeclaringClass())
                .returns(method.getGenericReturnType())
                .takes(method.getGenericParameterTypes())
                .build();
    }

    private static class MethodSignatureEquivalence extends Equivalence<Method> {

        @Override
        protected boolean doEquivalent(Method a, Method b) {
            return new EqualsBuilder()
                    .append(a.getName(), b.getName())
                    .append(a.getGenericReturnType(), b.getGenericReturnType())
                    .append(a.getGenericParameterTypes(), b.getGenericParameterTypes())
                    .isEquals();
        }

        @Override
        protected int doHash(Method method) {
            return new HashCodeBuilder()
                    .append(method.getName())
                    .append(method.getGenericReturnType())
                    .append(method.getGenericParameterTypes())
                    .toHashCode();
        }
    }
}
