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

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@ThreadSafe
public class ModelMapStrategy implements ModelSchemaExtractionStrategy {

    private static final ModelType<ModelMap<?>> MODEL_MAP_MODEL_TYPE = new ModelType<ModelMap<?>>() {
    };

    // TODO extract common stuff from this and ModelSet and reuse

    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store) {
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

            extractionContext.found(getModelSchema(extractionContext, elementType));
        }
    }

    private <T, E> ModelSchema<T> getModelSchema(ModelSchemaExtractionContext<T> extractionContext, ModelType<E> elementType) {
        ModelCollectionSchema<T, E> schema = new ModelCollectionSchema<T, E>(extractionContext.getType(), elementType);
        extractionContext.child(elementType, "element type");
        return schema;
    }

}
