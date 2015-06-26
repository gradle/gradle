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

import org.gradle.api.Nullable;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelMapGroovyDecorator;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Currently only handles interfaces with no type parameters that directly extend ModelMap.
 */
public class SpecializedMapStrategy implements ModelSchemaExtractionStrategy {
    private final ManagedCollectionProxyClassGenerator generator = new ManagedCollectionProxyClassGenerator();

    @Nullable
    @Override
    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store, ModelSchemaCache cache) {
        Type type = extractionContext.getType().getType();
        if (!(type instanceof Class)) {
            return null;
        }
        Class<?> contractType = (Class<?>) type;
        if (!contractType.isInterface()) {
            return null;
        }
        if (contractType.getGenericInterfaces().length != 1) {
            return null;
        }
        Type superType = contractType.getGenericInterfaces()[0];
        if (!(superType instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
        if (!parameterizedSuperType.getRawType().equals(ModelMap.class)) {
            return null;
        }
        ModelType<?> elementType = ModelType.of(parameterizedSuperType.getActualTypeArguments()[0]);
        Class<?> proxyImpl = generator.generate(ModelMapGroovyDecorator.class, contractType);
        return new ModelSchemaExtractionResult<T>(ModelSchema.specializedMap(extractionContext.getType(), elementType, proxyImpl));
    }

}
