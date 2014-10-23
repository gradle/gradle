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
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.concurrent.ExecutionException;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

@ThreadSafe
public class CachingModelSchemaStore implements ModelSchemaStore {

    private final ModelSchemaExtractor extractor;

    public CachingModelSchemaStore(ModelSchemaExtractor extractor) {
        this.extractor = extractor;
    }

    private final LoadingCache<ModelType<?>, ModelSchema<?>> schemas = CacheBuilder.newBuilder().build(new CacheLoader<ModelType<?>, ModelSchema<?>>() {
        @Override
        public ModelSchema<?> load(ModelType<?> key) throws Exception {
            return extractor.extract(key, CachingModelSchemaStore.this);
        }
    });

    public <T> ModelSchema<T> getSchema(ModelType<T> type) {
        try {
            ModelSchema<?> schema = schemas.get(type);
            return Cast.uncheckedCast(schema);
        } catch (ExecutionException e) {
            throw throwAsUncheckedException(e.getCause());
        } catch (UncheckedExecutionException e) {
            throw throwAsUncheckedException(e.getCause());
        }
    }

    public boolean isManaged(ModelType<?> type) {
        return extractor.isManaged(type.getRawClass());
    }
}
