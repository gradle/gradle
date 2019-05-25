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
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.internal.reflect.PropertyAccessorType;
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

    @Override
    public <R> void extract(final ModelSchemaExtractionContext<R> extractionContext) {
        ModelType<R> type = extractionContext.getType();
        if (!isTarget(type)) {
            return;
        }

        CandidateMethods candidateMethods = ModelSchemaUtils.getCandidateMethods(type.getRawClass());
        Iterable<ModelPropertyExtractionContext> candidateProperties = selectProperties(extractionContext, candidateMethods);

        List<ModelPropertyExtractionResult<?>> extractedProperties = extractProperties(candidateProperties);
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

    private Iterable<ModelPropertyExtractionContext> selectProperties(final ModelSchemaExtractionContext<?> context, CandidateMethods candidateMethods) {
        Map<String, ModelPropertyExtractionContext> propertiesMap = Maps.newTreeMap();
        for (Map.Entry<Wrapper<Method>, Collection<Method>> entry : candidateMethods.allMethods().entrySet()) {
            Method method = entry.getKey().get();
            PropertyAccessorType propertyAccessorType = PropertyAccessorType.of(method);
            Collection<Method> methodsWithEqualSignature = entry.getValue();
            if (propertyAccessorType != null) {
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
                return property.isReadable();
            }
        });
    }

    private static List<ModelPropertyExtractionResult<?>> extractProperties(Iterable<ModelPropertyExtractionContext> properties) {
        ImmutableList.Builder<ModelPropertyExtractionResult<?>> builder = ImmutableList.builder();
        for (ModelPropertyExtractionContext propertyContext : properties) {
            builder.add(extractProperty(propertyContext));
        }
        return builder.build();
    }

    private static ModelPropertyExtractionResult<?> extractProperty(ModelPropertyExtractionContext property) {
        ModelType<?> propertyType = determinePropertyType(property.getAccessor(PropertyAccessorType.GET_GETTER));
        if (propertyType == null) {
            propertyType = determinePropertyType(property.getAccessor(PropertyAccessorType.IS_GETTER));
        }
        if (propertyType == null) {
            propertyType = determinePropertyType(property.getAccessor(PropertyAccessorType.SETTER));
        }
        return createProperty(propertyType, property);
    }

    private static ModelType<?> determinePropertyType(PropertyAccessorExtractionContext accessor) {
        return accessor == null ? null : ModelType.of(accessor.getAccessorType().genericPropertyTypeFor(accessor.getMostSpecificDeclaration()));
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
