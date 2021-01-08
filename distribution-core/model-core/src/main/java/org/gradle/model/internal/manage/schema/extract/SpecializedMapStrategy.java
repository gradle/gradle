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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.NodeBackedModelMap;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.SpecializedMapSchema;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

/**
 * Currently only handles interfaces with no type parameters that directly extend ModelMap.
 */
public class SpecializedMapStrategy implements ModelSchemaExtractionStrategy {
    private final ManagedCollectionProxyClassGenerator generator = new ManagedCollectionProxyClassGenerator();
    private final LoadingCache<ModelType<?>, Class<?>> generatedImplementationTypes = CacheBuilder.newBuilder()
        .weakValues()
        .build(new CacheLoader<ModelType<?>, Class<?>>() {
            @Override
            public Class<?> load(ModelType<?> contractType) throws Exception {
                return generator.generate(NodeBackedModelMap.class, contractType.getConcreteClass());
            }
        });

    @Override
    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext) {
        ModelType<T> modelType = extractionContext.getType();
        if (!modelType.isClass()) {
            return;
        }
        Class<?> contractType = modelType.getConcreteClass();
        if (!contractType.isInterface()) {
            return;
        }
        if (contractType.getGenericInterfaces().length != 1) {
            return;
        }
        Type superType = contractType.getGenericInterfaces()[0];
        if (!(superType instanceof ParameterizedType)) {
            return;
        }
        ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
        if (!parameterizedSuperType.getRawType().equals(ModelMap.class)) {
            return;
        }
        ModelType<?> elementType = ModelType.of(parameterizedSuperType.getActualTypeArguments()[0]);
        Class<?> proxyImpl;
        try {
            proxyImpl = generatedImplementationTypes.get(modelType);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        extractionContext.found(getModelSchema(extractionContext, elementType, proxyImpl));
    }

    private <T, E> SpecializedMapSchema<T, E> getModelSchema(ModelSchemaExtractionContext<T> extractionContext, ModelType<E> elementType, Class<?> implementationType) {
        final SpecializedMapSchema<T, E> schema = new SpecializedMapSchema<T, E>(extractionContext.getType(), elementType, implementationType);
        extractionContext.child(elementType, "element type", new Action<ModelSchema<E>>() {
            @Override
            public void execute(ModelSchema<E> elementTypeSchema) {
                schema.setElementTypeSchema(elementTypeSchema);
            }
        });
        return schema;
    }

}
