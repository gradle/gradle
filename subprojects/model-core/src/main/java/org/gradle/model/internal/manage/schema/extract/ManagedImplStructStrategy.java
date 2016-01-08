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
import com.google.common.collect.*;
import org.gradle.api.Named;
import org.gradle.internal.reflect.MethodDescription;
import org.gradle.model.Managed;
import org.gradle.model.internal.manage.schema.ManagedImplStructSchema;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.*;
import static org.gradle.model.internal.manage.schema.extract.PropertyAccessorType.*;

public class ManagedImplStructStrategy extends StructSchemaExtractionStrategySupport {

    protected ManagedImplStructStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor);
    }

    protected boolean isTarget(ModelType<?> type) {
        return type.isAnnotationPresent(Managed.class);
    }

    @Override
    protected <R> void validateTypeHierarchy(final ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type) {
        walkTypeHierarchy(type.getConcreteClass(), new ModelSchemaUtils.TypeVisitor<R>() {
            @Override
            public void visitType(Class<? super R> type) {
                if (type.isAnnotationPresent(Managed.class)) {
                    validateManagedType(extractionContext, type);
                }
                validateType(extractionContext, type);
            }
        });
    }

    private void validateManagedType(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        if (!typeClass.isInterface() && !Modifier.isAbstract(typeClass.getModifiers())) {
            extractionContext.add("Must be defined as an interface or an abstract class.");
        }

        if (typeClass.getTypeParameters().length > 0) {
            extractionContext.add("Cannot be a parameterized type.");
        }
    }

    private void validateType(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        Constructor<?> customConstructor = findCustomConstructor(typeClass);
        if (customConstructor != null) {
            extractionContext.add(customConstructor, "Custom constructors are not supported.");
        }

        ensureNoInstanceScopedFields(extractionContext, typeClass);
        ensureNoProtectedOrPrivateMethods(extractionContext, typeClass);
    }

    private Constructor<?> findCustomConstructor(Class<?> typeClass) {
        Constructor<?>[] constructors = typeClass.getConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterTypes().length > 0) {
                return constructor;
            }
        }
        return null;
    }

    private void ensureNoInstanceScopedFields(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        List<Field> declaredFields = Arrays.asList(typeClass.getDeclaredFields());
        for (Field field : declaredFields) {
            int fieldModifiers = field.getModifiers();
            if (!field.isSynthetic() && !(Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers))) {
                extractionContext.add(field, "Fields must be static final.");
            }
        }
    }

    private void ensureNoProtectedOrPrivateMethods(ModelSchemaExtractionContext<?> extractionContext, Class<?> typeClass) {
        // sort for determinism
        Method[] methods = typeClass.getDeclaredMethods();
        Arrays.sort(methods, Ordering.usingToString());
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if (!method.isSynthetic() && !Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                extractionContext.add(method, "Protected and private methods are not supported.");
            }
        }
    }

    @Override
    protected void validateMethodDeclarationHierarchy(ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods) {
        for (String methodName : candidateMethods.methodNames()) {
            Collection<Equivalence.Wrapper<Method>> handledOverridden = Lists.newArrayList();
            if (!PropertyAccessorType.isPropertyMethodName(methodName)) {
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
        PropertyAccessorExtractionContext setter = property.getAccessor(SETTER);
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
    protected <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelProperty<?>> properties, Set<WeaklyTypeReferencingMethod<?, ?>> nonPropertyMethods, Iterable<ModelSchemaAspect> aspects) {
        return new ManagedImplStructSchema<R>(extractionContext.getType(), properties, nonPropertyMethods, aspects);
    }

    private InvalidManagedModelElementTypeException invalidMethod(ModelSchemaExtractionContext<?> extractionContext, String message, Method method) {
        return invalidMethod(extractionContext, message, MethodDescription.of(method));
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
