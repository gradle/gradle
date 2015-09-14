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
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.manage.schema.ManagedImplModelSchema;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public abstract class CollectionStrategy implements ModelSchemaExtractionStrategy {
    protected <T> void validateType(ModelType<?> modelType, ModelSchemaExtractionContext<T> extractionContext, ModelType<T> type) {
        if (!type.getRawClass().equals(modelType.getConcreteClass())) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format("subtyping %s is not supported", modelType.getConcreteClass().getName()));
        }
        if (type.isHasWildcardTypeVariables()) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type parameter of %s cannot be a wildcard", modelType.getConcreteClass().getName()));
        }

        List<ModelType<?>> typeVariables = type.getTypeVariables();
        if (typeVariables.isEmpty()) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type parameter of %s has to be specified", modelType.getConcreteClass().getName()));
        }
    }

    protected <T, E> ModelSchemaExtractionResult<T> getModelSchemaExtractionResult(ModelType<?> modelType, final ModelSchemaExtractionContext<T> extractionContext, final ModelSchemaCache cache, final ModelType<E> elementType, ModelSchemaStore store) {
        if (modelType.isAssignableFrom(elementType)) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format("%1$s cannot be used as type parameter of %1$s", modelType.getConcreteClass().getName()));
        }

        ModelCollectionSchema<T, E> schema = new ModelCollectionSchema<T, E>(extractionContext.getType(), elementType, this.<T, E>getNodeInitializer(store));
        ModelSchemaExtractionContext<?> typeParamExtractionContext = extractionContext.child(elementType, "element type", new Action<ModelSchemaExtractionContext<?>>() {
            public void execute(ModelSchemaExtractionContext<?> context) {
                ModelSchema<?> typeParamSchema = cache.get(elementType);

                if (!(typeParamSchema instanceof ManagedImplModelSchema)) {
                    throw new InvalidManagedModelElementTypeException(extractionContext, String.format(
                        "cannot create a managed set of type %s as it is an unmanaged type. Only @Managed types are allowed.",
                        elementType
                    ));
                }
            }
        });
        return new ModelSchemaExtractionResult<T>(schema, ImmutableList.of(typeParamExtractionContext));
    }

    protected abstract <T, E> Function<ModelCollectionSchema<T, E>, NodeInitializer> getNodeInitializer(ModelSchemaStore store);
}
