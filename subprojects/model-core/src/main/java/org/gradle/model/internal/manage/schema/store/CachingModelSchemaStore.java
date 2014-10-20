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

package org.gradle.model.internal.manage.schema.store;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.concurrent.ExecutionException;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class CachingModelSchemaStore implements ModelSchemaStore {

    private final ModelSchemaCacheLoader modelSchemaCacheLoader = new ModelSchemaCacheLoader();

    private final LoadingCache<ModelType<?>, ModelSchema<?>> schemas = CacheBuilder.newBuilder().build(modelSchemaCacheLoader);

    public <T> ModelSchema<T> getSchema(ModelType<T> type, ModelSchemaStore backingStore) {
        modelSchemaCacheLoader.setBackingStore(backingStore);
        try {
            ModelSchema<?> schema = schemas.get(type);
            return Cast.uncheckedCast(schema);
        } catch (ExecutionException e) {
            throw throwAsUncheckedException(e.getCause());
        } catch (UncheckedExecutionException e) {
            throw throwAsUncheckedException(e.getCause());
        }
    }

    private class ModelSchemaCacheLoader extends CacheLoader<ModelType<?>, ModelSchema<?>> {

        private ModelSchemaStore backingStore;

        public void setBackingStore(ModelSchemaStore backingStore) {
            this.backingStore = backingStore;
        }

        @Override
        public ModelSchema<?> load(ModelType<?> key) throws Exception {
            return backingStore.getSchema(key, CachingModelSchemaStore.this);
        }
    }
}
