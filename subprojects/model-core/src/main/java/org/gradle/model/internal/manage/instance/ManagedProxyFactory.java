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

package org.gradle.model.internal.manage.instance;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.manage.schema.extract.ManagedProxyClassGenerator;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ManagedProxyFactory {

    private final ManagedProxyClassGenerator proxyClassGenerator = new ManagedProxyClassGenerator();
    private final LoadingCache<CacheKey<?>, Class<?>> generatedImplementationTypes = CacheBuilder.newBuilder()
        .weakValues()
        .build(new CacheLoader<CacheKey<?>, Class<?>>() {
            @Override
            public Class<?> load(CacheKey<?> key) throws Exception {
                return proxyClassGenerator.generate(key.schema, key.delegateSchema);
            }
        });

    public <T> T createProxy(ModelElementState state, ModelStructSchema<T> schema, ModelStructSchema<? extends T> delegateSchema) {
        try {
            Class<? extends T> generatedClass = getGeneratedImplementation(schema, delegateSchema);
            if (generatedClass == null) {
                throw new IllegalStateException("No managed implementation class available for: " + schema.getType());
            }
            if (delegateSchema == null) {
                Constructor<? extends T> constructor = generatedClass.getConstructor(ModelElementState.class);
                return constructor.newInstance(state);
            } else {
                ModelType<? extends T> delegateType = delegateSchema.getType();
                Object delegate = state.getBackingNode().getPrivateData(delegateType);
                Constructor<? extends T> constructor = generatedClass.getConstructor(ModelElementState.class, delegateType.getConcreteClass());
                return constructor.newInstance(state, delegate);
            }
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getTargetException());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private <T> Class<? extends T> getGeneratedImplementation(ModelStructSchema<T> schema, ModelStructSchema<? extends T> delegateSchema) throws java.util.concurrent.ExecutionException {
        return Cast.uncheckedCast(generatedImplementationTypes.get(new CacheKey<T>(schema, delegateSchema)));
    }

    private static class CacheKey<T> {
        private final ModelStructSchema<T> schema;
        private final ModelStructSchema<? extends T> delegateSchema;

        private CacheKey(ModelStructSchema<T> schema, @Nullable ModelStructSchema<? extends T> delegateSchema) {
            this.schema = schema;
            this.delegateSchema = delegateSchema;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey<?> cacheKey = (CacheKey<?>) o;

            if (!schema.equals(cacheKey.schema)) {
                return false;
            }
            return !(delegateSchema != null ? !delegateSchema.equals(cacheKey.delegateSchema) : cacheKey.delegateSchema != null);

        }

        @Override
        public int hashCode() {
            int result = schema.hashCode();
            result = 31 * result + (delegateSchema != null ? delegateSchema.hashCode() : 0);
            return result;
        }
    }
}
