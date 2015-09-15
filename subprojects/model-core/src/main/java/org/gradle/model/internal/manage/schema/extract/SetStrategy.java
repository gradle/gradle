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

import org.gradle.api.Action;
import org.gradle.model.internal.core.ModelProjection;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.schema.ManagedImplModelSchema;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

public abstract class SetStrategy extends CollectionStrategy {

    private final ModelType<?> modelType;

    public SetStrategy(ModelType<?> modelType) {
        this.modelType = modelType;
    }

    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store) {
        ModelType<T> type = extractionContext.getType();
        if (modelType.isAssignableFrom(type)) {
           validateType(modelType, extractionContext, type);

            ModelType<?> elementType = type.getTypeVariables().get(0);

            extractionContext.found(getModelSchema(modelType, extractionContext, elementType));
        }
    }

    protected abstract <E> ModelProjection getProjection(ModelType<E> elementType, ModelSchemaStore schemaStore, NodeInitializerRegistry nodeInitializerRegistry);

    protected <T, E> ModelSchema<T> getModelSchema(ModelType<?> modelType, final ModelSchemaExtractionContext<T> extractionContext, final ModelType<E> elementType) {
        if (modelType.isAssignableFrom(elementType)) {
            throw new InvalidManagedModelElementTypeException(extractionContext, String.format("%1$s cannot be used as type parameter of %1$s", modelType.getConcreteClass().getName()));
        }

        ModelCollectionSchema<T, E> schema = new ModelCollectionSchema<T, E>(extractionContext.getType(), elementType);
        extractionContext.child(elementType, "element type", new Action<ModelSchema<E>>() {
            public void execute(ModelSchema<E> typeParamSchema) {
                if (!(typeParamSchema instanceof ManagedImplModelSchema)) {
                    throw new InvalidManagedModelElementTypeException(extractionContext, String.format(
                        "cannot create a managed set of type %s as it is an unmanaged type. Only @Managed types are allowed.",
                        elementType
                    ));
                }
            }
        });
        return schema;
    }
}
