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

import com.google.common.collect.ImmutableList;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.collection.internal.DefaultManagedSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.List;

@ThreadSafe
public class ManagedSetSchemaExtractionHandler extends AbstractModelSchemaExtractionHandler {
    public ManagedSetSchemaExtractionHandler() {
        super(new ModelType<ManagedSet<?>>() {});
    }

    public <T> ModelSchemaExtractionResult<T> extract(ModelType<T> type, ModelSchemaCache cache, ModelSchemaExtractionContext context) {
        List<ModelType<?>> typeVariables = type.getTypeVariables();

        if (typeVariables.isEmpty()) {
            throw invalid(type, String.format("type parameter of %s has to be specified", ManagedSet.class.getName()));
        }
        if (type.isHasWildcardTypeVariables()) {
            throw invalid(type, String.format("type parameter of %s cannot be a wildcard", ManagedSet.class.getName()));
        }

        ManagedSetInstantiator<T> elementInstantiator = new ManagedSetInstantiator<T>(cache);
        ModelSchema<T> schema = new ModelSchema<T>(type, elementInstantiator);
        elementInstantiator.setSchema(schema);
        ModelType<? extends ManagedSet<?>> managedSetSchema = Cast.uncheckedCast(type);
        return new ModelSchemaExtractionResult<T>(schema, ImmutableList.of(new ManagedSetElementTypeExtractionContext(managedSetSchema, context)));
    }

    private class ManagedSetInstantiator<T> implements Factory<T> {

        private final ModelSchemaCache cache;
        private ModelSchema<T> schema;

        ManagedSetInstantiator(ModelSchemaCache cache) {
            this.cache = cache;
        }

        public void setSchema(ModelSchema<T> schema) {
            this.schema = schema;
        }

        public T create() {
            final ModelSchema<?> elementSchema = cache.get(schema.getType().getTypeVariables().get(0));
            T set = Cast.uncheckedCast(createManagedSetInstance(elementSchema));
            return set;
        }

        private <P> DefaultManagedSet<P> createManagedSetInstance(final ModelSchema<P> elementSchema) {
            Factory<P> factory = new Factory<P>() {
                public P create() {
                    return elementSchema.createInstance();
                }
            };
            return new DefaultManagedSet<P>(factory);
        }
    }
}
