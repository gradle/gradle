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

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.internal.reflect.MethodDescription;
import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.*;
import java.util.*;

import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.*;
import static org.gradle.model.internal.manage.schema.extract.MethodType.*;

public class ManagedImplStructStrategy extends StructSchemaExtractionStrategySupport {

    protected ManagedImplStructStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
    }

    protected boolean isTarget(ModelType<?> type) {
        return type.getRawClass().isAnnotationPresent(Managed.class);
    }

    @Override
    protected <R> void validateTypeHierarchy(final ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type) {
        walkTypeHierarchy(type.getConcreteClass(), new ModelSchemaUtils.TypeVisitor<R>() {
            @Override
            public void visitType(Class<? super R> type) {
                if (type.isAnnotationPresent(Managed.class)) {
                    validateManagedType(extractionContext, type);
                }
            }
        });
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

    @Override
    protected void validateMethodDeclarationHierarchy(ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods) {
        for (String methodName : candidateMethods.methodNames()) {
            Collection<Equivalence.Wrapper<Method>> handledOverridden = Lists.newArrayList();
            if (!MethodType.isPropertyMethodName(methodName)) {
                Map<Equivalence.Wrapper<Method>, Collection<Method>> overridden = candidateMethods.overriddenMethodsNamed(methodName);
                if (!overridden.isEmpty()) {
                    handleOverriddenMethods(context, overridden.values());
                    handledOverridden.addAll(overridden.keySet());
                }
            }
            Map<Equivalence.Wrapper<Method>, Collection<Method>> overloaded = candidateMethods.overloadedMethodsNamed(methodName, handledOverridden);
            if (!overloaded.isEmpty()) {
                handleOverloadedMethods(context, Iterables.concat(overloaded.values()));
            }
        }
    }

    private void handleOverriddenMethods(ModelSchemaExtractionContext<?> extractionContext, Iterable<Collection<Method>> overriddenMethods) {
        ImmutableSet.Builder<Method> rejectedBuilder = ImmutableSet.builder();
        for (Collection<Method> methods : overriddenMethods) {
            if (methods.size() <= 1 || isMethodDeclaredInManagedType(Iterables.getLast(methods))) {
                rejectedBuilder.addAll(methods);
            }
        }
        ImmutableSet<Method> rejectedOverrides = rejectedBuilder.build();
        if (!rejectedOverrides.isEmpty() && isMethodDeclaredInManagedType(rejectedOverrides)) {
            throw invalidMethods(extractionContext, "overridden methods not supported", rejectedOverrides);
        }
    }

    private void handleOverloadedMethods(ModelSchemaExtractionContext<?> extractionContext, Iterable<Method> overloadedMethods) {
        if (isMethodDeclaredInManagedType(overloadedMethods)) {
            throw invalidMethods(extractionContext, "overloaded methods are not supported", overloadedMethods);
        }
    }

    @Override
    protected void handleNonPropertyMethod(ModelSchemaExtractionContext<?> context, Collection<Method> nonPropertyMethodsWithEqualSignature) {
        Method mostSpecificMethod = findMostSpecificMethod(nonPropertyMethodsWithEqualSignature);
        if (isMethodDeclaredInManagedType(mostSpecificMethod)) {
            String methodName = mostSpecificMethod.getName();
            if (isGetterName(methodName)) {
                if (!takesNoParameter(mostSpecificMethod)) {
                    throw invalidMethods(context, "getter methods cannot take parameters", nonPropertyMethodsWithEqualSignature);
                }
            }
            if (isSetterName(methodName)) {
                if (!hasVoidReturnType(mostSpecificMethod)) {
                    throw invalidMethods(context, "setter method must have void return type", nonPropertyMethodsWithEqualSignature);
                }
                if (!takesSingleParameter(mostSpecificMethod)) {
                    throw invalidMethods(context, "setter method must have exactly one parameter", nonPropertyMethodsWithEqualSignature);
                }
            }
            if (nonPropertyMethodsWithEqualSignature.size() > 1 && !isMethodDeclaredInManagedType(Iterables.getLast(nonPropertyMethodsWithEqualSignature))) {
                // TODO:PM here we allows unmanaged non-property methods overridden in managed type as their implementation should be provided by an unmanaged super-type default implementation, not enough context to validate this here
                return;
            }
            throw invalidMethods(context, "only paired getter/setter methods are supported", nonPropertyMethodsWithEqualSignature);
        }
    }

    @Override
    protected boolean selectProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property) {
        return true;
    }

    @Override
    protected void handleInvalidGetter(ModelSchemaExtractionContext<?> extractionContext, Method getter, String message) {
        if (ModelSchemaUtils.isMethodDeclaredInManagedType(getter)) {
            throw invalidMethod(extractionContext, message, getter);
        }
    }

    @Override
    protected void validateProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property) {
        PropertyAccessorExtractionContext mergedGetter = property.mergeGetters();
        PropertyAccessorExtractionContext setter = property.getSetter();
        if (setter != null) {
            Method mostSpecificSetter = setter.getMostSpecificDeclaration();
            if (mergedGetter == null) {
                throw invalidMethods(context, "only paired getter/setter methods are supported", setter.getDeclaringMethods());
            }
            if (setter.isDeclaredAsAbstract()) {
                if (!mergedGetter.isDeclaredAsAbstract()) {
                    throw invalidMethod(context, "setters are not allowed for non-abstract getters", mostSpecificSetter);
                }
            }
            if (mostSpecificSetter.getName().equals("setName") && Named.class.isAssignableFrom(context.getType().getRawClass())) {
                throw new InvalidManagedModelElementTypeException(context, String.format(
                    "@Managed types implementing %s must not declare a setter for the name property",
                    Named.class.getName()
                ));
            }
            if (mergedGetter.isDeclaredInManagedType() && !setter.isDeclaredInManagedType()) {
                throw invalidMethods(context, "unmanaged setter for managed getter", mergedGetter.getDeclaringMethods());
            }
            if (!mergedGetter.isDeclaredInManagedType() && setter.isDeclaredInManagedType()) {
                throw invalidMethods(context, "managed setter for unmanaged getter", mergedGetter.getDeclaringMethods());
            }
            if (!setter.isDeclaredInManagedType()) {
                return;
            }
            if (!Modifier.isAbstract(mostSpecificSetter.getModifiers())) {
                throw invalidMethod(context, "non-abstract setters are not allowed", mostSpecificSetter);
            }
            ModelType<?> propertyType = ModelType.returnType(mergedGetter.getMostSpecificDeclaration());
            ModelType<?> setterType = ModelType.paramType(mostSpecificSetter, 0);
            if (!propertyType.equals(setterType)) {
                String message = "setter method param must be of exactly the same type as the getter returns (expected: " + propertyType + ", found: " + setterType + ")";
                throw invalidMethod(context, message, mostSpecificSetter);
            }
        }
    }

    @Override
    protected ModelProperty.StateManagementType determineStateManagementType(ModelSchemaExtractionContext<?> extractionContext, PropertyAccessorExtractionContext getterContext) {
        // Named.getName() needs to be handled specially
        if (getterContext.getMostSpecificDeclaration().getName().equals("getName")
            && Named.class.isAssignableFrom(extractionContext.getType().getRawClass())) {
            return ModelProperty.StateManagementType.MANAGED;
        }

        if (getterContext.isDeclaredInManagedType()) {
            if (getterContext.isDeclaredAsAbstract()) {
                return ModelProperty.StateManagementType.MANAGED;
            } else {
                return ModelProperty.StateManagementType.UNMANAGED;
            }
        } else {
            return ModelProperty.StateManagementType.UNMANAGED;
        }
    }

    @Override
    protected <P> Action<ModelSchema<P>> createPropertyValidator(final ModelSchemaExtractionContext<?> parentContext, final ModelPropertyExtractionResult<P> propertyResult) {
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
                boolean isAllowedPropertyTypeOfManagedType = propertySchema instanceof ManagedImplSchema
                    || propertySchema instanceof ScalarValueSchema;

                boolean isDeclaredAsHavingUnmanagedType = propertyResult.getGetter().isAnnotationPresent(Unmanaged.class);

                if (isAllowedPropertyTypeOfManagedType && isDeclaredAsHavingUnmanagedType) {
                    throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                        "property '%s' is marked as @Unmanaged, but is of @Managed type '%s'. Please remove the @Managed annotation.%n",
                        property.getName(), property.getType()
                    ));
                }

                if (!property.isWritable()) {
                    if (isDeclaredAsHavingUnmanagedType) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "unmanaged property '%s' cannot be read only, unmanaged properties must have setters",
                            property.getName())
                        );
                    }
                }

                if (propertySchema instanceof CollectionSchema) {
                    if (!(propertySchema instanceof ScalarCollectionSchema) && property.isWritable()) {
                        throw new InvalidManagedModelElementTypeException(parentContext, String.format(
                            "property '%s' cannot have a setter (%s properties must be read only).",
                            property.getName(), property.getType().toString()));
                    }
                }
            }
        };
    }

    @Override
    protected <R> ManagedImplStructSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelPropertyExtractionResult<?>> propertyResults, Iterable<ModelSchemaAspect> aspects) {
        ModelType<R> type = extractionContext.getType();
        Iterable<ModelProperty<?>> properties = Iterables.transform(propertyResults, new Function<ModelPropertyExtractionResult<?>, ModelProperty<?>>() {
            @Override
            public ModelProperty<?> apply(ModelPropertyExtractionResult<?> propertyResult) {
                return propertyResult.getProperty();
            }
        });
        return new ManagedImplStructSchema<R>(type, properties, aspects);
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
