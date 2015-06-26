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
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public abstract class SetStrategy implements ModelSchemaExtractionStrategy {

    private final ModelType<?> modelType;

    public SetStrategy(ModelType<?> modelType) {
        this.modelType = modelType;
    }

    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store, final ModelSchemaCache cache) {
        ModelType<T> type = extractionContext.getType();
        if (modelType.isAssignableFrom(type)) {
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

            ModelType<?> elementType = typeVariables.get(0);

            return gettModelSchemaExtractionResult(extractionContext, cache, elementType, store);
        } else {
            return null;
        }
    }

    private <T, E> ModelSchemaExtractionResult<T> gettModelSchemaExtractionResult(ModelSchemaExtractionContext<T> extractionContext, final ModelSchemaCache cache, ModelType<E> elementType, ModelSchemaStore store) {
        if (modelType.isAssignableFrom(elementType)) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format("%1$s cannot be used as type parameter of %1$s", modelType.getConcreteClass().getName()));
        }

        ModelCollectionSchema<T, E> schema = ModelSchema.collection(extractionContext.getType(), elementType, this.<T, E>getNodeInitializer(store));
        ModelSchemaExtractionContext<?> typeParamExtractionContext = extractionContext.child(elementType, "element type", new Action<ModelSchemaExtractionContext<?>>() {
            public void execute(ModelSchemaExtractionContext<?> context) {
                ModelSchema<?> typeParamSchema = cache.get(context.getType());

                if (!typeParamSchema.getKind().isManaged()) {
                    throw new InvalidManagedModelElementTypeException(context.getParent(), String.format(
                        "cannot create a managed set of type %s as it is an unmanaged type. Only @Managed types are allowed.",
                        context.getType()
                    ));
                }
            }
        });
        return new ModelSchemaExtractionResult<T>(schema, ImmutableList.of(typeParamExtractionContext));
    }

    protected abstract <T, E> Function<ModelCollectionSchema<T, E>, NodeInitializer> getNodeInitializer(ModelSchemaStore store);

}
