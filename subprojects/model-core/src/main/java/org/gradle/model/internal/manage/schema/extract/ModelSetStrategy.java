/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.model.ModelSet;
import org.gradle.model.internal.manage.schema.CollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSetSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class ModelSetStrategy implements ModelSchemaExtractionStrategy {

    private final ModelType<?> modelType;

    public ModelSetStrategy() {
        modelType = new ModelType<ModelSet<?>>() {
        };
    }

    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext) {
        ModelType<T> type = extractionContext.getType();
        if (modelType.isAssignableFrom(type)) {
            validateType(modelType, extractionContext, type);

            ModelType<?> elementType = type.getTypeVariables().get(0);

            extractionContext.found(getModelSchema(extractionContext, elementType));
        }
    }

    private <T> void validateType(ModelType<?> modelType, ModelSchemaExtractionContext<T> extractionContext, ModelType<T> type) {
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

    private <T, E> ModelSchema<T> getModelSchema(final ModelSchemaExtractionContext<T> extractionContext, final ModelType<E> elementType) {
        final CollectionSchema<T, E> schema = new ModelSetSchema<T, E>(extractionContext.getType(), elementType);
        extractionContext.child(elementType, "element type", new Action<ModelSchema<E>>() {
            @Override
            public void execute(ModelSchema<E> elementTypeSchema) {
                schema.setElementTypeSchema(elementTypeSchema);
            }
        });
        return schema;
    }
}
