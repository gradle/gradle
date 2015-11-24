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

import com.google.common.base.*;
import com.google.common.collect.*;
import org.gradle.api.Action;
import org.gradle.model.Unmanaged;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.util.*;

public abstract class StructSchemaExtractionStrategySupport implements ModelSchemaExtractionStrategy {

    private final ModelSchemaAspectExtractor aspectExtractor;

    protected StructSchemaExtractionStrategySupport(ModelSchemaAspectExtractor aspectExtractor) {
        this.aspectExtractor = aspectExtractor;
    }

    public <R> void extract(final ModelSchemaExtractionContext<R> extractionContext) {
        ModelType<R> type = extractionContext.getType();
        if (!isTarget(type)) {
            return;
        }

        validateTypeHierarchy(extractionContext, type);

        CandidateMethods candidateMethods = ModelSchemaUtils.getCandidateMethods(type.getRawClass());
        validateMethodDeclarationHierarchy(extractionContext, candidateMethods);

        Iterable<ModelPropertyExtractionContext> candidateProperties = selectProperties(extractionContext, candidateMethods);
        validateProperties(extractionContext, candidateProperties);

        List<ModelPropertyExtractionResult<?>> extractedProperties = extractProperties(extractionContext, candidateProperties);
        List<ModelSchemaAspect> aspects = aspectExtractor.extract(extractionContext, extractedProperties);

        ModelSchema<R> schema = createSchema(extractionContext, extractedProperties, aspects);
        for (ModelPropertyExtractionResult<?> propertyResult : extractedProperties) {
            toPropertyExtractionContext(extractionContext, propertyResult);
        }

        extractionContext.found(schema);
    }

    protected abstract boolean isTarget(ModelType<?> type);

    protected abstract <R> void validateTypeHierarchy(ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type);

    protected abstract void validateMethodDeclarationHierarchy(ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods);

    private Iterable<ModelPropertyExtractionContext> selectProperties(final ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods) {
        Map<String, ModelPropertyExtractionContext> propertiesMap = Maps.newTreeMap();
        for (Map.Entry<Equivalence.Wrapper<Method>, Collection<Method>> entry : candidateMethods.allMethods().entrySet()) {
            Method method = entry.getKey().get();
            MethodType methodType = MethodType.of(method);
            Collection<Method> methodsWithEqualSignature = entry.getValue();
            if (MethodType.NON_PROPERTY == methodType) {
                handleNonPropertyMethod(context, methodsWithEqualSignature);
            } else {
                String propertyName = methodType.propertyNameFor(method);
                ModelPropertyExtractionContext propertyContext = propertiesMap.get(propertyName);
                if (propertyContext == null) {
                    propertyContext = new ModelPropertyExtractionContext(propertyName);
                    propertiesMap.put(propertyName, propertyContext);
                }
                if (MethodType.GET_GETTER == methodType) {
                    propertyContext.setGetGetter(new PropertyAccessorExtractionContext(methodsWithEqualSignature));
                } else if (MethodType.IS_GETTER == methodType) {
                    propertyContext.setIsGetter(new PropertyAccessorExtractionContext(methodsWithEqualSignature));
                } else {
                    propertyContext.setSetter(new PropertyAccessorExtractionContext(methodsWithEqualSignature));
                }
            }
        }
        return Collections2.filter(propertiesMap.values(), new Predicate<ModelPropertyExtractionContext>() {
            @Override
            public boolean apply(ModelPropertyExtractionContext property) {
                return selectProperty(context, property);
            }
        });
    }

    protected abstract void handleNonPropertyMethod(ModelSchemaExtractionContext<?> context, Collection<Method> nonPropertyMethodsWithEqualSignature);

    protected abstract boolean selectProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property);

    private void validateProperties(ModelSchemaExtractionContext<?> context, Iterable<ModelPropertyExtractionContext> properties) {
        for (ModelPropertyExtractionContext property : properties) {
            PropertyAccessorExtractionContext getGetter = property.getGetGetter();
            PropertyAccessorExtractionContext isGetter = property.getIsGetter();
            if (getGetter != null && isGetter != null) {
                Method getMethod = getGetter.getMostSpecificDeclaration();
                Method isMethod = isGetter.getMostSpecificDeclaration();
                if (getMethod.getReturnType() != boolean.class || isMethod.getReturnType() != boolean.class) {
                    handleInvalidGetter(context, isMethod,
                        String.format("property '%s' has both '%s()' and '%s()' getters, but they don't both return a boolean",
                            property.getPropertyName(), isMethod.getName(), getMethod.getName()));
                }
            }
            if (isGetter != null) {
                Method isMethod = isGetter.getMostSpecificDeclaration();
                if (isMethod.getReturnType() != boolean.class) {
                    handleInvalidGetter(context, isMethod, "getter method name must start with 'get'");
                }
            }
            validateProperty(context, property);
        }
    }

    protected abstract void handleInvalidGetter(ModelSchemaExtractionContext<?> extractionContext, Method getter, String message);

    protected abstract void validateProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property);

    private List<ModelPropertyExtractionResult<?>> extractProperties(ModelSchemaExtractionContext<?> context, Iterable<ModelPropertyExtractionContext> properties) {
        ImmutableList.Builder<ModelPropertyExtractionResult<?>> builder = ImmutableList.builder();
        for (ModelPropertyExtractionContext propertyContext : properties) {
            builder.add(extractProperty(context, propertyContext));
        }
        return builder.build();
    }

    private <R> ModelPropertyExtractionResult<R> extractProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property) {
        PropertyAccessorExtractionContext gettersContext = property.mergeGetters();
        final ModelType<R> returnType = ModelType.returnType(gettersContext.getMostSpecificDeclaration());

        WeaklyTypeReferencingMethod<?, Void> setterRef;
        PropertyAccessorExtractionContext setterContext = property.getSetter();
        if (setterContext != null) {
            Method mostSpecificDeclaration = setterContext.getMostSpecificDeclaration();
            setterRef = WeaklyTypeReferencingMethod.of(ModelType.of(mostSpecificDeclaration.getDeclaringClass()), ModelType.of(void.class), mostSpecificDeclaration);
        } else {
            setterRef = null;
        }

        ImmutableSet<ModelType<?>> declaringClasses = ImmutableSet.copyOf(Iterables.transform(gettersContext.getDeclaringMethods(), new Function<Method, ModelType<?>>() {
            public ModelType<?> apply(Method input) {
                return ModelType.of(input.getDeclaringClass());
            }
        }));

        List<WeaklyTypeReferencingMethod<?, R>> getterRefs = Lists.newArrayList(Iterables.transform(gettersContext.getGetters(), new Function<Method, WeaklyTypeReferencingMethod<?, R>>() {
            @Override
            public WeaklyTypeReferencingMethod<?, R> apply(Method getter) {
                return WeaklyTypeReferencingMethod.of(ModelType.of(getter.getDeclaringClass()), returnType, getter);
            }
        }));

        ModelProperty.StateManagementType stateManagementType = determineStateManagementType(context, gettersContext);
        boolean declaredAsHavingUnmanagedType = gettersContext.getAnnotation(Unmanaged.class) != null;

        return new ModelPropertyExtractionResult<R>(
            new ModelProperty<R>(returnType, property.getPropertyName(), stateManagementType, declaringClasses, getterRefs, setterRef, declaredAsHavingUnmanagedType),
            gettersContext,
            setterContext
        );
    }

    protected abstract ModelProperty.StateManagementType determineStateManagementType(ModelSchemaExtractionContext<?> extractionContext, PropertyAccessorExtractionContext getterContext);

    private <R, P> void toPropertyExtractionContext(ModelSchemaExtractionContext<R> parentContext, ModelPropertyExtractionResult<P> propertyResult) {
        ModelProperty<P> property = propertyResult.getProperty();
        String propertyDescription = propertyDescription(parentContext, property);
        Action<ModelSchema<P>> propertyValidator = createPropertyValidator(parentContext, propertyResult);
        parentContext.child(property.getType(), propertyDescription, attachSchema(property, propertyValidator));
    }

    private <P> Action<? super ModelSchema<P>> attachSchema(final ModelProperty<P> property, final Action<ModelSchema<P>> propertyValidator) {
        return new Action<ModelSchema<P>>() {
            @Override
            public void execute(ModelSchema<P> propertySchema) {
                property.setSchema(propertySchema);
                propertyValidator.execute(propertySchema);
            }
        };
    }

    private String propertyDescription(ModelSchemaExtractionContext<?> parentContext, ModelProperty<?> property) {
        if (property.getDeclaredBy().size() == 1 && property.getDeclaredBy().contains(parentContext.getType())) {
            return String.format("property '%s'", property.getName());
        } else {
            ImmutableSortedSet<String> declaredBy = ImmutableSortedSet.copyOf(Iterables.transform(property.getDeclaredBy(), Functions.toStringFunction()));
            return String.format("property '%s' declared by %s", property.getName(), Joiner.on(", ").join(declaredBy));
        }
    }

    protected abstract <P> Action<ModelSchema<P>> createPropertyValidator(ModelSchemaExtractionContext<?> extractionContext, ModelPropertyExtractionResult<P> propertyResult);

    protected abstract <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelPropertyExtractionResult<?>> propertyResults, Iterable<ModelSchemaAspect> aspects);
}
