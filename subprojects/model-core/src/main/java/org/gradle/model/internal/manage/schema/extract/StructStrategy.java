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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.model.Managed;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ThreadSafe
public class StructStrategy implements ModelSchemaExtractionStrategy {

    private final Factory<String> supportedTypeDescriptions;
    private final ModelSchemaExtractor extractor;

    public StructStrategy(ModelSchemaExtractor extractor, Factory<String> supportedTypeDescriptions) {
        this.extractor = extractor;
        this.supportedTypeDescriptions = supportedTypeDescriptions;
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton("interfaces annotated with " + Managed.class.getName());
    }

    public <R> ModelSchemaExtractionResult<R> extract(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        if (type.getRawClass().isAnnotationPresent(Managed.class)) {
            validateType(type, extractionContext);

            Iterable<ModelProperty<?>> superTypeProperties = extractSuperTypeProperties(extractionContext, cache);

            List<Method> methodList = Arrays.asList(type.getRawClass().getDeclaredMethods());
            if (methodList.isEmpty()) {
                return new ModelSchemaExtractionResult<R>(ModelSchema.struct(type, superTypeProperties));
            }

            List<ModelProperty<?>> properties = Lists.newLinkedList();

            Map<String, Method> methods = Maps.newHashMap();
            for (Method method : methodList) {
                String name = method.getName();
                if (methods.containsKey(name)) {
                    throw invalidMethod(extractionContext, name, "overloaded methods are not supported.");
                }
                methods.put(name, method);
            }

            List<String> methodNames = Lists.newLinkedList(methods.keySet());
            List<String> handled = Lists.newArrayList();

            for (String methodName : methodNames) {
                Method method = methods.get(methodName);
                if (methodName.startsWith("get") && !methodName.equals("get")) {
                    if (method.getParameterTypes().length != 0) {
                        throw invalidMethod(extractionContext, methodName, "getter methods cannot take parameters.");
                    }

                    Character getterPropertyNameFirstChar = methodName.charAt(3);
                    if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                        throw invalidMethod(extractionContext, methodName, "the 4th character of the getter method name must be an uppercase character.");
                    }

                    ModelType<?> returnType = ModelType.of(method.getGenericReturnType());

                    String propertyNameCapitalized = methodName.substring(3);
                    String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
                    String setterName = "set" + propertyNameCapitalized;
                    boolean isWritable = methods.containsKey(setterName);

                    if (isWritable) {
                        validateSetter(extractionContext, returnType, methods.get(setterName));
                        handled.add(setterName);
                    }

                    properties.add(ModelProperty.of(returnType, propertyName, isWritable));
                    handled.add(methodName);
                }
            }

            methodNames.removeAll(handled);

            // TODO - should call out valid getters without setters
            if (!methodNames.isEmpty()) {
                throw new InvalidManagedModelElementTypeException(extractionContext, "only paired getter/setter methods are supported (invalid methods: [" + Joiner.on(", ").join(methodNames) + "]).");
            }

            ModelSchema<R> schema = ModelSchema.struct(type, Iterables.concat(properties, superTypeProperties));

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

    private <R> Iterable<ModelSchema<? super R>> extractSuperTypeSchemas(final ModelSchemaExtractionContext<R> parentContext, Iterable<ModelType<? super R>> superTypes, final ModelSchemaCache cache) {
        return Iterables.transform(superTypes, new Function<ModelType<? super R>, ModelSchema<? super R>>() {
            public ModelSchema<? super R> apply(ModelType<? super R> superType) {
                return extractor.extract(parentContext.child(superType, "super type"), cache);
            }
        });
    }

    private <R> Iterable<ModelProperty<?>> extractSuperTypeProperties(ModelSchemaExtractionContext<R> extractionContext, ModelSchemaCache cache) {
        Iterable<ModelSchema<? super R>> superTypeSchemas = extractSuperTypeSchemas(extractionContext, extractionContext.getType().getSuperTypes(), cache);
        Optional<ModelSchema<? super R>> unmanagedSuperTypeSchema = Iterables.tryFind(superTypeSchemas, new Predicate<ModelSchema<? super R>>() {
            public boolean apply(ModelSchema<? super R> superTypeSchema) {
                return superTypeSchema.getKind() != ModelSchema.Kind.STRUCT;
            }
        });
        if (unmanagedSuperTypeSchema.isPresent()) {
            String message = String.format("extends %s but extending unmanaged types is not supported", unmanagedSuperTypeSchema.get().getType());
            throw new InvalidManagedModelElementTypeException(extractionContext, message);
        }
        return Iterables.concat(Iterables.transform(superTypeSchemas, new Function<ModelSchema<? super R>, Iterable<ModelProperty<?>>>() {
            public Iterable<ModelProperty<?>> apply(ModelSchema<? super R> superTypeSchema) {
                return superTypeSchema.getProperties().values();
            }
        }));
    }

    private <R, P> ModelSchemaExtractionContext<P> toPropertyExtractionContext(final ModelSchemaExtractionContext<R> parentContext, final ModelProperty<P> property, final ModelSchemaCache modelSchemaCache) {
        return parentContext.child(property.getType(), String.format("property '%s'", property.getName()), new Action<ModelSchemaExtractionContext<P>>() {
            public void execute(ModelSchemaExtractionContext<P> propertyExtractionContext) {
                ModelSchema<P> propertySchema = modelSchemaCache.get(property.getType());

                if (propertySchema.getKind() == ModelSchema.Kind.UNMANAGED) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "type %s cannot be used for property '%s' as it is an unmanaged type.%n%s",
                            property.getType(), property.getName(), supportedTypeDescriptions.create()
                    ));
                }

                if (!property.isWritable()) {
                    if (!propertySchema.getKind().equals(ModelSchema.Kind.STRUCT)) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format("read only property '%s' has non managed type %s, only managed types can be used", property.getName(), property.getType()));
                    }
                }
            }
        });
    }

    private void validateSetter(ModelSchemaExtractionContext<?> extractionContext, ModelType<?> propertyType, Method setter) {
        if (!setter.getReturnType().equals(void.class)) {
            throw invalidMethod(extractionContext, setter.getName(), "setter method must have void return type");
        }

        Type[] setterParameterTypes = setter.getGenericParameterTypes();
        if (setterParameterTypes.length != 1) {
            throw invalidMethod(extractionContext, setter.getName(), "setter method must have exactly one parameter");
        }

        ModelType<?> setterType = ModelType.of(setterParameterTypes[0]);
        if (!setterType.equals(propertyType)) {
            String message = "setter method param must be of exactly the same type as the getter returns (expected: " + propertyType + ", found: " + setterType + ")";
            throw invalidMethod(extractionContext, setter.getName(), message);
        }
    }

    public void validateType(ModelType<?> type, ModelSchemaExtractionContext<?> extractionContext) {
        Class<?> typeClass = type.getConcreteClass();

        if (!typeClass.isInterface()) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "must be defined as an interface.");
        }

        if (typeClass.getTypeParameters().length > 0) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "cannot be a parameterized type.");
        }
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelSchemaExtractionContext<?> extractionContext, String methodName, String message) {
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (method: " + methodName + ").");
    }
}
