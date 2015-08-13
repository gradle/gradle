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
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.getOverloadedMethods;
import static org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils.isMethodDeclaredInManagedType;

public abstract class ImplTypeSchemaExtractionStrategySupport implements ModelSchemaExtractionStrategy {

    private final ModelSchemaAspectExtractor aspectExtractor;

    protected ImplTypeSchemaExtractionStrategySupport(ModelSchemaAspectExtractor aspectExtractor) {
        this.aspectExtractor = aspectExtractor;
    }

    protected abstract boolean isTarget(ModelType<?> type);

    public <R> ModelSchemaExtractionResult<R> extract(final ModelSchemaExtractionContext<R> extractionContext, ModelSchemaStore store, final ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        if (!isTarget(type)) {
            return null;
        }

        validateTypeHierarchy(extractionContext, type);

        List<ModelProperty<?>> properties = extractPropertySchemas(extractionContext, ModelSchemaUtils.getCandidateMethods(type.getRawClass()));
        List<ModelSchemaAspect> aspects = aspectExtractor.extract(extractionContext, properties);

        ModelSchema<R> schema = createSchema(extractionContext, store, type, properties, aspects);
        Iterable<ModelSchemaExtractionContext<?>> propertyDependencies = Iterables.transform(properties, new Function<ModelProperty<?>, ModelSchemaExtractionContext<?>>() {
            public ModelSchemaExtractionContext<?> apply(final ModelProperty<?> property) {
                return toPropertyExtractionContext(extractionContext, property, cache);
            }
        });

        return new ModelSchemaExtractionResult<R>(schema, propertyDependencies);
    }

    private <R, P> ModelSchemaExtractionContext<P> toPropertyExtractionContext(ModelSchemaExtractionContext<R> parentContext, ModelProperty<P> property, ModelSchemaCache modelSchemaCache) {
        return parentContext.child(property.getType(), propertyDescription(parentContext, property), createPropertyValidator(property, modelSchemaCache));
    }

    private <R> List<ModelProperty<?>> extractPropertySchemas(ModelSchemaExtractionContext<R> extractionContext, Multimap<String, Method> methodsByName) {
        List<ModelProperty<?>> properties = Lists.newArrayList();
        Set<Method> handledMethods = Sets.newHashSet();

        List<String> methodNames = Lists.newArrayList(methodsByName.keySet());
        Collections.sort(methodNames);
        for (String methodName : methodNames) {
            Collection<Method> methods = methodsByName.get(methodName);

            List<Method> overloadedMethods = getOverloadedMethods(methods);
            if (overloadedMethods != null) {
                handleOverloadedMethods(extractionContext, overloadedMethods);
                continue;
            }

            if (methodName.startsWith("get") && !methodName.equals("get")) {
                PropertyAccessorExtractionContext getterContext = new PropertyAccessorExtractionContext(methods, isGetterDefinedInManagedType(extractionContext, methodName, methods));

                Character getterPropertyNameFirstChar = methodName.charAt(3);
                if (!Character.isUpperCase(getterPropertyNameFirstChar)) {
                    handleInvalidGetter(extractionContext, getterContext, "the 4th character of the getter method name must be an uppercase character");
                    continue;
                }

                String propertyNameCapitalized = methodName.substring(3);
                String propertyName = StringUtils.uncapitalize(propertyNameCapitalized);
                String setterName = "set" + propertyNameCapitalized;
                Collection<Method> setterMethods = methodsByName.get(setterName);
                PropertyAccessorExtractionContext setterContext = !setterMethods.isEmpty() ? new PropertyAccessorExtractionContext(setterMethods) : null;

                ModelProperty<?> property = extractPropertySchema(extractionContext, propertyName, getterContext, setterContext);
                if (property != null) {
                    properties.add(property);

                    handledMethods.addAll(getterContext.getDeclaringMethods());
                    if (setterContext != null) {
                        handledMethods.addAll(setterContext.getDeclaringMethods());
                    }
                }
            }
        }

        validateAllNecessaryMethodsHandled(extractionContext, methodsByName.values(), handledMethods);
        return properties;
    }

    @Nullable
    private <R> ModelProperty<?> extractPropertySchema(ModelSchemaExtractionContext<?> extractionContext, String propertyName, PropertyAccessorExtractionContext getterContext, PropertyAccessorExtractionContext setterContext) {
        // Take the most specific declaration of the getter
        Method mostSpecificGetter = getterContext.getMostSpecificDeclaration();
        if (mostSpecificGetter.getParameterTypes().length != 0) {
            handleInvalidGetter(extractionContext, getterContext, "getter methods cannot take parameters");
            return null;
        }

        if (!getterContext.isDeclaredInManagedType() && mostSpecificGetter.getReturnType().isPrimitive()) {
            handleInvalidGetter(extractionContext, getterContext, "managed properties cannot have primitive types");
            return null;
        }

        boolean managedProperty = getterContext.isDeclaredInManagedType() && getterContext.isDeclaredAsAbstract();
        ModelType<R> returnType = ModelType.returnType(mostSpecificGetter);

        boolean writable = setterContext != null;
        if (writable) {
            validateSetter(extractionContext, returnType, getterContext, setterContext);
        }

        Map<Class<? extends Annotation>, Annotation> annotations = getAnnotations(getterContext.getDeclaringMethods());
        Map<Class<? extends Annotation>, Annotation> setterAnnotations;
        if (setterContext != null) {
            setterAnnotations = getAnnotations(setterContext.getDeclaringMethods());
        } else {
            setterAnnotations = Collections.emptyMap();
        }

        ImmutableSet<ModelType<?>> declaringClasses = ImmutableSet.copyOf(Iterables.transform(getterContext.getDeclaringMethods(), new Function<Method, ModelType<?>>() {
            public ModelType<?> apply(Method input) {
                return ModelType.of(input.getDeclaringClass());
            }
        }));

        WeaklyTypeReferencingMethod<?, R> getterRef = WeaklyTypeReferencingMethod.of(extractionContext.getType(), returnType, getterContext.getMostSpecificDeclaration());
        return ModelProperty.of(returnType, propertyName, managedProperty, writable, declaringClasses, getterRef, annotations, setterAnnotations);
    }

    private Map<Class<? extends Annotation>, Annotation> getAnnotations(Collection<Method> methods) {
        Map<Class<? extends Annotation>, Annotation> annotations = Maps.newLinkedHashMap();
        for (Method method : methods) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                // Make sure more specific annotation doesn't get overwritten with less specific one
                if (!annotations.containsKey(annotation.annotationType())) {
                    annotations.put(annotation.annotationType(), annotation);
                }
            }
        }
        return annotations;
    }

    protected boolean isGetterDefinedInManagedType(ModelSchemaExtractionContext<?> extractionContext, String methodName, Collection<Method> getterMethods) {
        return isMethodDeclaredInManagedType(getterMethods);
    }

    protected abstract void validateAllNecessaryMethodsHandled(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> allMethods, final Set<Method> handledMethods);

    protected abstract <R> void validateTypeHierarchy(ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type);

    protected abstract void handleInvalidGetter(ModelSchemaExtractionContext<?> extractionContext, PropertyAccessorExtractionContext getter, String message);

    protected abstract void handleOverloadedMethods(ModelSchemaExtractionContext<?> extractionContext, Collection<Method> overloadedMethods);

    protected abstract <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, ModelSchemaStore store, ModelType<R> type, List<ModelProperty<?>> properties, List<ModelSchemaAspect> aspects);

    protected abstract <P> Action<ModelSchemaExtractionContext<P>> createPropertyValidator(ModelProperty<P> property, ModelSchemaCache modelSchemaCache);

    private String propertyDescription(ModelSchemaExtractionContext<?> parentContext, ModelProperty<?> property) {
        if (property.getDeclaredBy().size() == 1 && property.getDeclaredBy().contains(parentContext.getType())) {
            return String.format("property '%s'", property.getName());
        } else {
            ImmutableSortedSet<String> declaredBy = ImmutableSortedSet.copyOf(Iterables.transform(property.getDeclaredBy(), Functions.toStringFunction()));
            return String.format("property '%s' declared by %s", property.getName(), Joiner.on(", ").join(declaredBy));
        }
    }

    protected abstract void validateSetter(ModelSchemaExtractionContext<?> extractionContext, ModelType<?> propertyType, PropertyAccessorExtractionContext getterContext, PropertyAccessorExtractionContext setterContext);
}
