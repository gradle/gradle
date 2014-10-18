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

package org.gradle.model.internal.manage.schema.extraction;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.internal.Cast;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.state.ManagedModelElementInstanceFactory;

import java.util.concurrent.ExecutionException;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class DefaultModelSchemaStore implements ModelSchemaStore {

    private final ModelSchemaExtractor extractor;

    private final LoadingCache<Class<?>, ModelSchema<?>> schemas = CacheBuilder.newBuilder().build(new CacheLoader<Class<?>, ModelSchema<?>>() {
        @Override
        public ModelSchema<?> load(Class<?> key) throws Exception {
            return extractor.extract(key);
        }
    });

    public DefaultModelSchemaStore(ManagedModelElementInstanceFactory managedElementFactory) {
        extractor = new ModelSchemaExtractor(this, managedElementFactory);
    }

    public <T> ModelSchema<T> getSchema(Class<T> type) {
        ModelSchema<?> schema;
        try {
            schema = schemas.get(type);
            return Cast.uncheckedCast(schema);
        } catch (ExecutionException e) {
            throw throwAsUncheckedException(e.getCause());
        } catch (UncheckedExecutionException e) {
            throw throwAsUncheckedException(e.getCause());
        }
    }

    public boolean isManaged(Class<?> type) {
        return extractor.isManaged(type);
    }
}
