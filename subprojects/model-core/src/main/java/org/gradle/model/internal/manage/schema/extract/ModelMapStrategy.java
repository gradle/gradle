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
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.model.Managed;
import org.gradle.model.ModelMap;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.inspect.ManagedChildNodeCreatorStrategy;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@ThreadSafe
public class ModelMapStrategy implements ModelSchemaExtractionStrategy {

    private static final ModelType<ModelMap<?>> MODEL_MAP_MODEL_TYPE = new ModelType<ModelMap<?>>() {
    };

    // TODO extract common stuff from this and ModelSet and reuse

    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store, final ModelSchemaCache cache) {
        ModelType<T> type = extractionContext.getType();
        if (MODEL_MAP_MODEL_TYPE.isAssignableFrom(type)) {
            if (!type.getRawClass().equals(ModelMap.class)) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("subtyping %s is not supported.", ModelMap.class.getName()));
            }
            if (type.isHasWildcardTypeVariables()) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type parameter of %s cannot be a wildcard.", ModelMap.class.getName()));
            }

            List<ModelType<?>> typeVariables = type.getTypeVariables();
            if (typeVariables.isEmpty()) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type parameter of %s has to be specified.", ModelMap.class.getName()));
            }

            ModelType<?> elementType = typeVariables.get(0);

            if (MODEL_MAP_MODEL_TYPE.isAssignableFrom(elementType)) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("%1$s cannot be used as type parameter of %1$s.", ModelMap.class.getName()));
            }

            return gettModelSchemaExtractionResult(extractionContext, cache, elementType, store);
        } else {
            return null;
        }
    }

    private <T, E> ModelSchemaExtractionResult<T> gettModelSchemaExtractionResult(ModelSchemaExtractionContext<T> extractionContext, final ModelSchemaCache cache, ModelType<E> elementType, final ModelSchemaStore store) {
        ModelCollectionSchema<T, E> schema = ModelSchema.collection(extractionContext.getType(), elementType, new Function<ModelCollectionSchema<T, E>, NodeInitializer>() {
            @Override
            public NodeInitializer apply(ModelCollectionSchema<T, E> input) {
                final ManagedChildNodeCreatorStrategy<E> childCreator = new ManagedChildNodeCreatorStrategy<E>(store);
                return new ProjectionOnlyNodeInitializer(ModelMapModelProjection.managed(input.getElementType(), childCreator));
            }
        });
        ModelSchemaExtractionContext<?> typeParamExtractionContext = extractionContext.child(elementType, "element type", new Action<ModelSchemaExtractionContext<?>>() {
            public void execute(ModelSchemaExtractionContext<?> context) {
                ModelType<?> elementType = context.getType();
                ModelSchema<?> typeParamSchema = cache.get(elementType);

                if (!typeParamSchema.getKind().isManaged()) {
                    throw new InvalidManagedModelElementTypeException(context.getParent(), String.format(
                        "cannot create a model map of type %s as it is not a %s type.",
                        elementType, Managed.class.getName()
                    ));
                }
            }
        });
        return new ModelSchemaExtractionResult<T>(schema, ImmutableList.of(typeParamExtractionContext));
    }

}
