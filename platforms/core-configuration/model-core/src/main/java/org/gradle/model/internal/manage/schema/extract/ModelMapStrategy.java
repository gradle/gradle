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
import org.gradle.model.ModelMap;
import org.gradle.model.internal.manage.schema.ModelMapSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class ModelMapStrategy implements ModelSchemaExtractionStrategy {

    private static final ModelType<ModelMap<?>> MODEL_MAP_MODEL_TYPE = new ModelType<ModelMap<?>>() {
    };

    // TODO extract common stuff from this and ModelSet and reuse

    @Override
    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext) {
        ModelType<T> type = extractionContext.getType();
        if (MODEL_MAP_MODEL_TYPE.isAssignableFrom(type)) {
            if (!type.getRawClass().equals(ModelMap.class)) {
                extractionContext.add(String.format("subtyping %s is not supported.", ModelMap.class.getName()));
                return;
            }

            if (type.isHasWildcardTypeVariables()) {
                extractionContext.add(String.format("type parameter of %s cannot be a wildcard.", ModelMap.class.getName()));
                return;
            }

            List<ModelType<?>> typeVariables = type.getTypeVariables();
            if (typeVariables.isEmpty()) {
                extractionContext.add(String.format("type parameter of %s has to be specified.", ModelMap.class.getName()));
                return;
            }

            ModelType<?> elementType = typeVariables.get(0);
            extractionContext.found(getModelSchema(extractionContext, elementType));
        }
    }

    private <T, E> ModelSchema<T> getModelSchema(ModelSchemaExtractionContext<T> extractionContext, ModelType<E> elementType) {
        final ModelMapSchema<T, E> schema = new ModelMapSchema<T, E>(extractionContext.getType(), elementType);
        extractionContext.child(elementType, "element type", new Action<ModelSchema<E>>() {
            @Override
            public void execute(ModelSchema<E> elementTypeSchema) {
                schema.setElementTypeSchema(elementTypeSchema);
            }
        });
        return schema;
    }
}
