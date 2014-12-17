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

import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;

public class EnumStrategy implements ModelSchemaExtractionStrategy {

    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaCache cache) {
        ModelType<T> type = extractionContext.getType();
        if (type.getRawClass().isEnum()) {
            return new ModelSchemaExtractionResult<T>(ModelSchema.value(type));
        } else {
            return null;
        }
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton("enum types");
    }

}
