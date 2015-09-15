/*
 * Copyright 2015 the original author or authors.
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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.internal.reflect.MethodDescription;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.isMethodDeclaredInManagedType;
import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.walkTypeHierarchy;

public abstract class ManagedImplStructSchemaExtractionStrategySupport extends StructSchemaExtractionStrategySupport {

    private final ManagedProxyClassGenerator classGenerator = new ManagedProxyClassGenerator();

    private final Class<?> implementedInterface;
    private final Class<?> delegateType;

    protected ManagedImplStructSchemaExtractionStrategySupport(ModelSchemaAspectExtractor aspectExtractor, Class<?> delegateType, Class<?> implementedInterface) {
        super(aspectExtractor);
        this.implementedInterface = implementedInterface;
        this.delegateType = delegateType;
    }

    @Override
    @SuppressWarnings("SimplifiableIfStatement")
    protected boolean isTarget(ModelType<?> type) {
        if (!type.getRawClass().isAnnotationPresent(Managed.class)) {
            return false;
        }
        return implementedInterface == null
            || (!type.getRawClass().equals(implementedInterface)
                && implementedInterface.isAssignableFrom(type.getRawClass()));
    }

    @Override
    protected <R> void validateTypeHierarchy(final ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type) {
        walkTypeHierarchy(type.getConcreteClass(), new ModelSchemaUtils.TypeVisitor() {
            @Override
            public void visitType(Class<?> type) {
                if (type.isAnnotationPresent(Managed.class)) {
                    validateManagedType(extractionContext, type);
                }
            }
        });
    }

    @Override
    protected void handleOverloadedMethods(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> overloadedMethods) {
        if (isMethodDeclaredInManagedType(overloadedMethods)) {
            throw invalidMethods(extractionContext, "overloaded methods are not supported", overloadedMethods);
        }
    }

    @Override
    protected ModelProperty.StateManagementType determineStateManagementType(ModelSchemaExtractionContext<?> extractionContext, PropertyAccessorExtractionContext getterContext) {
        // Named.getName() needs to be handled specially
        if (getterContext.getMostSpecificDeclaration().getName().equals("getName")
            && Named.class.isAssignableFrom(extractionContext.getType().getRawClass())) {
            if (delegateType == null) {
                return ModelProperty.StateManagementType.MANAGED;
            }
            boolean delegateHasGetNameMethod = Iterables.any(Arrays.asList(delegateType.getMethods()), new Predicate<Method>() {
                @Override
                public boolean apply(Method method) {
                    return method.getName().equals("getName");
                }
            });
            if (delegateHasGetNameMethod) {
                return ModelProperty.StateManagementType.DELEGATED;
            } else {
                return ModelProperty.StateManagementType.MANAGED;
            }
        }

        if (getterContext.isDeclaredInManagedType()) {
            if (getterContext.isDeclaredAsAbstract()) {
                return ModelProperty.StateManagementType.MANAGED;
            } else {
                return ModelProperty.StateManagementType.UNMANAGED;
            }
        } else {
            return ModelProperty.StateManagementType.DELEGATED;
        }
    }

    @Override
    protected <R> ModelManagedImplStructSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelPropertyExtractionResult<?>> propertyResults, Iterable<ModelSchemaAspect> aspects) {
        ModelType<R> type = extractionContext.getType();
        Class<? extends R> implClass = classGenerator.generate(type, delegateType, propertyResults);
        Iterable<ModelProperty<?>> properties = Iterables.transform(propertyResults, new Function<ModelPropertyExtractionResult<?>, ModelProperty<?>>() {
            @Override
            public ModelProperty<?> apply(ModelPropertyExtractionResult<?> propertyResult) {
                return propertyResult.getProperty();
            }
        });
        return new ModelManagedImplStructSchema<R>(type, properties, aspects, implClass, delegateType);
    }

    @Override
    protected void handleInvalidGetter(ModelSchemaExtractionContext<?> extractionContext, Method getter, String message) {
        if (ModelSchemaUtils.isMethodDeclaredInManagedType(getter)) {
            throw invalidMethod(extractionContext, message, getter);
        }
    }

    @Override
    protected void validateSetter(ModelSchemaExtractionContext<?> extractionContext, ModelType<?> propertyType, PropertyAccessorExtractionContext getterContext, PropertyAccessorExtractionContext setterContext) {
        // Get most specific setter
        Method mostSpecificSetter = setterContext.getMostSpecificDeclaration();

        if (!getterContext.isDeclaredAsAbstract() && setterContext.isDeclaredAsAbstract()) {
            throw invalidMethod(extractionContext, "setters are not allowed for non-abstract getters", mostSpecificSetter);
        }

        if (mostSpecificSetter.getName().equals("setName") && Named.class.isAssignableFrom(extractionContext.getType().getRawClass())) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format(
                "@Managed types implementing %s must not declare a setter for the name property",
                Named.class.getName()
            ));
        } else {
            if (getterContext.isDeclaredInManagedType() && !setterContext.isDeclaredInManagedType()) {
                throw invalidMethods(extractionContext, "unmanaged setter for managed getter", Iterables.concat(getterContext.getDeclaringMethods(), setterContext.getDeclaringMethods()));
            } else if (!getterContext.isDeclaredInManagedType() && setterContext.isDeclaredInManagedType()) {
                throw invalidMethods(extractionContext, "managed setter for unmanaged getter", Iterables.concat(getterContext.getDeclaringMethods(), setterContext.getDeclaringMethods()));
            }
        }

        if (!setterContext.isDeclaredInManagedType()) {
            return;
        }

        if (!Modifier.isAbstract(mostSpecificSetter.getModifiers())) {
            throw invalidMethod(extractionContext, "non-abstract setters are not allowed", mostSpecificSetter);
        }

        if (!mostSpecificSetter.getReturnType().equals(void.class)) {
            throw invalidMethod(extractionContext, "setter method must have void return type", mostSpecificSetter);
        }

        Type[] setterParameterTypes = mostSpecificSetter.getGenericParameterTypes();
        if (setterParameterTypes.length != 1) {
            throw invalidMethod(extractionContext, "setter method must have exactly one parameter", mostSpecificSetter);
        }

        ModelType<?> setterType = ModelType.paramType(mostSpecificSetter, 0);
        if (!setterType.equals(propertyType)) {
            String message = "setter method param must be of exactly the same type as the getter returns (expected: " + propertyType + ", found: " + setterType + ")";
            throw invalidMethod(extractionContext, message, mostSpecificSetter);
        }
    }

    @Override
    protected void validateAllNecessaryMethodsHandled(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> allMethods, final Set<Method> handledMethods) {
        Iterable<Method> notHandled = Iterables.filter(allMethods, new Predicate<Method>() {
            @Override
            public boolean apply(Method method) {
                return method.getDeclaringClass().isAnnotationPresent(Managed.class) && !handledMethods.contains(method);
            }
        });

        // TODO - should call out valid getters without setters
        if (!Iterables.isEmpty(notHandled)) {
            throw invalidMethods(extractionContext, "only paired getter/setter methods are supported", notHandled);
        }
    }

    @Override
    protected <P> Action<ModelSchema<P>> createPropertyValidator(final ModelSchemaExtractionContext<?> parentContext, final ModelPropertyExtractionResult<P> propertyResult, final ModelSchemaStore modelSchemaStore) {
        return new Action<ModelSchema<P>>() {
            @Override
            public void execute(ModelSchema<P> propertySchema) {
                ModelProperty<P> property = propertyResult.getProperty();
                // Do not validate unmanaged properties
                if (!property.getStateManagementType().equals(ModelProperty.StateManagementType.MANAGED)) {
                    return;
                }

                // The "name" property is handled differently if type implements Named
                if (property.getName().equals("name") && Named.class.isAssignableFrom(parentContext.getType().getRawClass())) {
                    return;
                }

                // Only managed implementation and value types are allowed as a managed property type unless marked with @Unmanaged
                boolean isAllowedPropertyTypeOfManagedType = propertySchema instanceof ManagedImplModelSchema
                    || propertySchema instanceof ModelValueSchema;
                boolean isDeclaredAsHavingUnmanagedType = propertyResult.getGetter().isAnnotationPresent(Unmanaged.class);

                if (isAllowedPropertyTypeOfManagedType && isDeclaredAsHavingUnmanagedType) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                        "property '%s' is marked as @Unmanaged, but is of @Managed type '%s'. Please remove the @Managed annotation.%n",
                        property.getName(), property.getType()
                    ));
                }

                if (!isAllowedPropertyTypeOfManagedType && !isDeclaredAsHavingUnmanagedType) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                        "type %s cannot be used for property '%s' as it is an unmanaged type (please annotate the getter with @org.gradle.model.Unmanaged if you want this property to be unmanaged).%n%s",
                        property.getType(), property.getName(), ModelSchemaExtractor.getManageablePropertyTypesDescription()
                    ));
                }

                if (!property.isWritable()) {
                    if (isDeclaredAsHavingUnmanagedType) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "unmanaged property '%s' cannot be read only, unmanaged properties must have setters",
                            property.getName())
                        );
                    }

                    if (!(propertySchema instanceof ManagedImplModelSchema)) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "read only property '%s' has non managed type %s, only managed types can be used",
                            property.getName(), property.getType()));
                    }
                }

                if (propertySchema instanceof ModelCollectionSchema) {
                    ModelCollectionSchema<P, ?> propertyCollectionsSchema = (ModelCollectionSchema<P, ?>) propertySchema;

                    ModelType<?> elementType = propertyCollectionsSchema.getElementType();
                    ModelSchema<?> elementTypeSchema = modelSchemaStore.getSchema(elementType);

                    if (propertySchema instanceof ScalarCollectionSchema) {
                        if (!ScalarTypes.isScalarType(elementType)) {
                            throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                                "property '%s' cannot be a collection of type %s as it is not a scalar type.",
                                property.getName(), elementType));
                        }
                    } else {
                        if (property.isWritable()) {
                            throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                                "property '%s' cannot have a setter (%s properties must be read only).",
                                property.getName(), property.getType().toString()));
                        }
                        if (!(elementTypeSchema instanceof ManagedImplModelSchema)) {
                            throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                                "property '%s' cannot be a model map of type %s as it is not a %s type.",
                                property.getName(), elementType, Managed.class.getName()
                            ));
                        }
                    }
                }
            }
        };
    }

    private void validateManagedType(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
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

    private InvalidManagedModelElementTypeException invalidMethods(ModelSchemaExtractionContext<?> extractionContext, String message, Iterable<Method> methods) {
        final ImmutableSortedSet<String> descriptions = ImmutableSortedSet.copyOf(Iterables.transform(methods, new Function<Method, String>() {
            public String apply(Method method) {
                return MethodDescription.of(method).toString();
            }
        }));
        return new InvalidManagedModelElementTypeException(extractionContext, message + " (invalid methods: " + Joiner.on(", ").join(descriptions) + ").");
    }
}
