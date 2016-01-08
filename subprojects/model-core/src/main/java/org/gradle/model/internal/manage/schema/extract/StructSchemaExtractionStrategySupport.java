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

import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.gradle.api.Action;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        if (extractionContext.hasProblems()) {
            return;
        }

        CandidateMethods candidateMethods = ModelSchemaUtils.getCandidateMethods(type.getRawClass());
        validateMethodDeclarationHierarchy(extractionContext, candidateMethods);

        Iterable<ModelPropertyExtractionContext> candidateProperties = selectProperties(extractionContext, candidateMethods);
        validateProperties(extractionContext, candidateProperties);

        List<ModelPropertyExtractionResult<?>> extractedProperties = extractProperties(extractionContext, candidateProperties);
        List<ModelSchemaAspect> aspects = aspectExtractor.extract(extractionContext, extractedProperties);

        Set<WeaklyTypeReferencingMethod<?, ?>> nonPropertyMethods = getNonPropertyMethods(candidateMethods, extractedProperties);
        Iterable<ModelProperty<?>> properties = Iterables.transform(extractedProperties, new Function<ModelPropertyExtractionResult<?>, ModelProperty<?>>() {
            @Override
            public ModelProperty<?> apply(ModelPropertyExtractionResult<?> propertyResult) {
                return propertyResult.getProperty();
            }
        });

        ModelSchema<R> schema = createSchema(extractionContext, properties, nonPropertyMethods, aspects);
        for (ModelPropertyExtractionResult<?> propertyResult : extractedProperties) {
            toPropertyExtractionContext(extractionContext, propertyResult);
        }

        extractionContext.found(schema);
    }

    private Set<WeaklyTypeReferencingMethod<?, ?>> getNonPropertyMethods(CandidateMethods candidateMethods, List<ModelPropertyExtractionResult<?>> extractedProperties) {
        Set<Method> nonPropertyMethods = Sets.newLinkedHashSet(Iterables.transform(candidateMethods.allMethods().keySet(), new Function<Wrapper<Method>, Method>() {
            @Override
            public Method apply(Wrapper<Method> method) {
                return method.get();
            }
        }));
        for (ModelPropertyExtractionResult<?> extractedProperty : extractedProperties) {
            for (PropertyAccessorExtractionContext accessor : extractedProperty.getAccessors()) {
                nonPropertyMethods.removeAll(accessor.getDeclaringMethods());
            }
        }
        return Sets.newLinkedHashSet(Iterables.transform(nonPropertyMethods, new Function<Method, WeaklyTypeReferencingMethod<?, ?>>() {
            @Override
            public WeaklyTypeReferencingMethod<?, ?> apply(Method method) {
                return WeaklyTypeReferencingMethod.of(method);
            }
        }));
    }

    protected abstract boolean isTarget(ModelType<?> type);

    protected abstract <R> void validateTypeHierarchy(ModelSchemaExtractionContext<R> extractionContext, ModelType<R> type);

    protected abstract void validateMethodDeclarationHierarchy(ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods);

    private Iterable<ModelPropertyExtractionContext> selectProperties(final ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods) {
        Map<String, ModelPropertyExtractionContext> propertiesMap = Maps.newTreeMap();
        for (Map.Entry<Wrapper<Method>, Collection<Method>> entry : candidateMethods.allMethods().entrySet()) {
            Method method = entry.getKey().get();
            PropertyAccessorType propertyAccessorType = PropertyAccessorType.of(method);
            Collection<Method> methodsWithEqualSignature = entry.getValue();
            if (propertyAccessorType == null) {
                handleNonPropertyMethod(context, methodsWithEqualSignature);
            } else {
                String propertyName = propertyAccessorType.propertyNameFor(method);
                ModelPropertyExtractionContext propertyContext = propertiesMap.get(propertyName);
                if (propertyContext == null) {
                    propertyContext = new ModelPropertyExtractionContext(propertyName);
                    propertiesMap.put(propertyName, propertyContext);
                }
                propertyContext.addAccessor(new PropertyAccessorExtractionContext(propertyAccessorType, methodsWithEqualSignature));
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
            PropertyAccessorExtractionContext getGetter = property.getAccessor(PropertyAccessorType.GET_GETTER);
            PropertyAccessorExtractionContext isGetter = property.getAccessor(PropertyAccessorType.IS_GETTER);
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

    private static List<ModelPropertyExtractionResult<?>> extractProperties(ModelSchemaExtractionContext<?> context, Iterable<ModelPropertyExtractionContext> properties) {
        ImmutableList.Builder<ModelPropertyExtractionResult<?>> builder = ImmutableList.builder();
        for (ModelPropertyExtractionContext propertyContext : properties) {
            builder.add(extractProperty(context, propertyContext));
        }
        return builder.build();
    }

    private static <R> ModelPropertyExtractionResult<R> extractProperty(ModelSchemaExtractionContext<?> context, ModelPropertyExtractionContext property) {
        PropertyAccessorExtractionContext gettersContext = property.mergeGetters();
        final ModelType<R> returnType = ModelType.returnType(gettersContext.getMostSpecificDeclaration());
        return createProperty(returnType, property);
    }

    private static <P> ModelPropertyExtractionResult<P> createProperty(ModelType<P> propertyType, ModelPropertyExtractionContext propertyContext) {
        ImmutableMap.Builder<PropertyAccessorType, WeaklyTypeReferencingMethod<?, ?>> accessors = ImmutableMap.builder();
        for (PropertyAccessorExtractionContext accessor : propertyContext.getAccessors()) {
            WeaklyTypeReferencingMethod<?, ?> accessorMethod = WeaklyTypeReferencingMethod.of(accessor.getMostSpecificDeclaration());
            accessors.put(accessor.getAccessorType(), accessorMethod);
        }
        ModelProperty<P> property = new ModelProperty<P>(
            propertyType,
            propertyContext.getPropertyName(),
            propertyContext.getDeclaredBy(),
            accessors.build()
        );
        return new ModelPropertyExtractionResult<P>(property, propertyContext.getAccessors());
    }

    private static <R, P> void toPropertyExtractionContext(ModelSchemaExtractionContext<R> parentContext, ModelPropertyExtractionResult<P> propertyResult) {
        ModelProperty<P> property = propertyResult.getProperty();
        String propertyDescription = propertyDescription(parentContext, property);
        parentContext.child(property.getType(), propertyDescription, attachSchema(property));
    }

    private static <P> Action<? super ModelSchema<P>> attachSchema(final ModelProperty<P> property) {
        return new Action<ModelSchema<P>>() {
            @Override
            public void execute(ModelSchema<P> propertySchema) {
                property.setSchema(propertySchema);
            }
        };
    }

    private static String propertyDescription(ModelSchemaExtractionContext<?> parentContext, ModelProperty<?> property) {
        if (property.getDeclaredBy().size() == 1 && property.getDeclaredBy().contains(parentContext.getType())) {
            return String.format("property '%s'", property.getName());
        } else {
            ImmutableSortedSet<String> declaredBy = ImmutableSortedSet.copyOf(Iterables.transform(property.getDeclaredBy(), Functions.toStringFunction()));
            return String.format("property '%s' declared by %s", property.getName(), Joiner.on(", ").join(declaredBy));
        }
    }

    protected abstract <R> ModelSchema<R> createSchema(ModelSchemaExtractionContext<R> extractionContext, Iterable<ModelProperty<?>> properties, Set<WeaklyTypeReferencingMethod<?, ?>> nonPropertyMethods, Iterable<ModelSchemaAspect> aspects);
}
