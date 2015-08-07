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
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.*;

public abstract class ImplTypeSchemaExtractionStrategySupport implements ModelSchemaExtractionStrategy {

    protected abstract boolean isTarget(ModelType<?> type);

    public <R> ModelSchemaExtractionResult<R> extract(final ModelSchemaExtractionContext<R> extractionContext, ModelSchemaStore store, final ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        if (!isTarget(type)) {
            return null;
        }

        validateTypeHierarchy(extractionContext, type);

        Multimap<String, Method> methodsByName = ModelSchemaUtils.getCandidateMethods(type.getRawClass());

        List<ModelProperty<?>> properties = Lists.newArrayList();
        final Set<Method> handledMethods = Sets.newHashSet();

        for (String methodName : methodsByName.keySet()) {
            Collection<Method> methods = methodsByName.get(methodName);
            if (hasOverloadedMethods(extractionContext, methodName, methods)) {
                continue;
            }

            if (methodName.startsWith("get") && !methodName.equals("get")) {
                Character getterPropertyNameFirstChar = methodName.charAt(3);
                if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                    invalidGetterNoUppercase(extractionContext, methods.iterator().next());
                    return null;
                }

                String propertyNameCapitalized = methodName.substring(3);
                String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
                String setterName = "set" + propertyNameCapitalized;
                Collection<Method> setterMethods = methodsByName.get(setterName);

                ModelProperty<?> property = createProperty(extractionContext, propertyName, methodName, methods, setterMethods, handledMethods);
                if (property != null) {
                    properties.add(property);
                }
            }
        }

        validateAllNecessaryMethodsHandled(extractionContext, methodsByName.values(), handledMethods);

        final ModelSchema<R> schema = createSchema(extractionContext, store, type, properties, type.getConcreteClass());
        Iterable<ModelSchemaExtractionContext<?>> propertyDependencies = Iterables.transform(properties, new Function<ModelProperty<?>, ModelSchemaExtractionContext<?>>() {
            public ModelSchemaExtractionContext<?> apply(final ModelProperty<?> property) {
                return toPropertyExtractionContext(extractionContext, property, cache);
            }
        });

        return new ModelSchemaExtractionResult<R>(schema, propertyDependencies);
    }

    protected abstract boolean hasOverloadedMethods(ModelSchemaExtractionContext<?> extractionContext, String methodName, Collection<Method> methods);

    protected abstract void validateAllNecessaryMethodsHandled(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> allMethods, final Set<Method> handledMethods);

    protected abstract <R> void validateTypeHierarchy(ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type);

    @Nullable
    protected ModelProperty<?> createProperty(ModelSchemaExtractionContext<?> extractionContext, String propertyName, String methodName, Collection<Method> getterMethods, Collection<Method> setterMethods, Set<Method> handledMethods) {
        // Take the most specific declaration of the getter
        Method mostSpecificGetter = getterMethods.iterator().next();
        boolean getterDeclaredInManagedType = isGetterDefinedInManagedType(extractionContext, methodName, getterMethods);

        if (mostSpecificGetter.getParameterTypes().length != 0) {
            invalidGetterHasParameterTypes(extractionContext, mostSpecificGetter);
            return null;
        }

        boolean abstractGetter = Modifier.isAbstract(mostSpecificGetter.getModifiers());
        if (!getterDeclaredInManagedType && mostSpecificGetter.getReturnType().isPrimitive()) {
            invalidGetterHasPrimitiveType(extractionContext, mostSpecificGetter);
            return null;
        }

        boolean managedProperty = getterDeclaredInManagedType && abstractGetter;
        ModelType<?> returnType = ModelType.returnType(mostSpecificGetter);

        boolean writable = !setterMethods.isEmpty();
        if (writable) {
            // Get most specific setter
            Method setter = setterMethods.iterator().next();

            boolean abstractSetter = Modifier.isAbstract(setter.getModifiers());
            if (!abstractGetter && abstractSetter) {
                throw invalidMethod(extractionContext, "setters are not allowed for non-abstract getters", setter);
            }

            boolean setterDeclaredInManagedType = isMethodDeclaredInManagedType(setterMethods);
            if (getterDeclaredInManagedType && !setterDeclaredInManagedType) {
                throw invalidMethods(extractionContext, "unmanaged setter for managed getter", Iterables.concat(getterMethods, setterMethods));
            } else if (!getterDeclaredInManagedType && setterDeclaredInManagedType) {
                throw invalidMethods(extractionContext, "managed setter for unmanaged getter", Iterables.concat(getterMethods, setterMethods));
            }

            if (setterDeclaredInManagedType) {
                validateSetter(extractionContext, returnType, setter);
            }
            handledMethods.addAll(setterMethods);
        }

        handledMethods.addAll(getterMethods);

        Map<Class<? extends Annotation>, Annotation> annotations = Maps.newLinkedHashMap();
        for (Method getterMethod : getterMethods) {
            for (Annotation annotation : getterMethod.getDeclaredAnnotations()) {
                if (!annotations.containsKey(annotation.annotationType())) {
                    annotations.put(annotation.annotationType(), annotation);
                }
            }
        }

        ImmutableSet<ModelType<?>> declaringClasses = ImmutableSet.copyOf(Iterables.transform(getterMethods, new Function<Method, ModelType<?>>() {
            public ModelType<?> apply(Method input) {
                return ModelType.of(input.getDeclaringClass());
            }
        }));

        return ModelProperty.of(returnType, propertyName, managedProperty, writable, declaringClasses, annotations);
    }

    protected abstract void invalidGetterHasParameterTypes(ModelSchemaExtractionContext<?> extractionContext, Method getter);

    protected abstract void invalidGetterNoUppercase(ModelSchemaExtractionContext<?> extractionContext, Method getter);

    protected abstract void invalidGetterHasPrimitiveType(ModelSchemaExtractionContext<?> extractionContext, Method getter);

    protected boolean isGetterDefinedInManagedType(ModelSchemaExtractionContext<?> extractionContext, String methodName, Collection<Method> getterMethods) {
        return isMethodDeclaredInManagedType(getterMethods);
    }

    protected abstract <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, ModelSchemaStore store, ModelType<R> type, List<ModelProperty<?>> properties, Class<R> concreteClass);

    private <R, P> ModelSchemaExtractionContext<P> toPropertyExtractionContext(ModelSchemaExtractionContext<R> parentContext, ModelProperty<P> property, ModelSchemaCache modelSchemaCache) {
        return parentContext.child(property.getType(), propertyDescription(parentContext, property), createPropertyValidator(property, modelSchemaCache));
    }

    protected abstract <P> Action<ModelSchemaExtractionContext<P>> createPropertyValidator(final ModelProperty<P> property, final ModelSchemaCache modelSchemaCache);

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
}
