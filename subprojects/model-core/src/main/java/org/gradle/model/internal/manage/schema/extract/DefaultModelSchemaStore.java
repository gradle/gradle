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

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

@ThreadSafe
public class DefaultModelSchemaStore implements ModelSchemaStore {
    final Object lock = new Object();
    final ModelSchemaCache cache = new ModelSchemaCache();
    final ModelSchemaExtractor schemaExtractor;

    public DefaultModelSchemaStore(ModelSchemaExtractor schemaExtractor) {
        this.schemaExtractor = schemaExtractor;
    }

    @Override
    public <T> ModelSchema<T> getSchema(ModelType<T> type) {
        synchronized (lock) {
            ModelSchema<T> schema = cache.get(type);
            if (schema != null) {
                return schema;
            }
            return schemaExtractor.extract(type, cache);
        }
    }

    @Override
    public <T> ModelSchema<T> getSchema(Class<T> type) {
        return getSchema(ModelType.of(type));
    }

    @Override
    public void cleanUp() {
        synchronized (lock) {
            cache.cleanUp();
        }
    }

    public long size() {
        synchronized (lock) {
            return cache.size();
        }
    }

}
