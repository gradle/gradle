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

import com.google.common.collect.ImmutableMap;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.Map;

public class PrimitiveStrategy implements ModelSchemaExtractionStrategy {

    private final static Map<ModelType<?>, Class<?>> BOXED_REPLACEMENTS = ImmutableMap.<ModelType<?>, Class<?>>builder()
            .put(ModelType.of(Boolean.TYPE), Boolean.class)
            .put(ModelType.of(Character.TYPE), Character.class)
            .put(ModelType.of(Float.TYPE), Double.class)
            .put(ModelType.of(Integer.TYPE), Integer.class)
            .put(ModelType.of(Long.TYPE), Long.class)
            .put(ModelType.of(Short.TYPE), Integer.class)
            .put(ModelType.of(Double.TYPE), Double.class)
            .build();


    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaCache cache) {
        ModelType<T> type = extractionContext.getType();
        if (type.getRawClass().isPrimitive()) {
            Class<?> replacementType = BOXED_REPLACEMENTS.get(type);
            if (replacementType != null) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type is not supported, please use %s instead", replacementType.getName()));
            }
        }

        return null;
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.emptySet();
    }

}
