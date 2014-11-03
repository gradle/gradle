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
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.collection.internal.DefaultManagedSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.List;

@ThreadSafe
public class ManagedSetSchemaExtractionStrategy<T extends ManagedSet<?>> implements ModelSchemaExtractionStrategy<T> {

    private final ModelType<T> type;

    private static final ModelType<ManagedSet<?>> MANAGED_SET_MODEL_TYPE = new ModelType<ManagedSet<?>>() {
    };

    public ModelType<T> getType() {
        return type;
    }

    public Spec<? super ModelType<? extends T>> getSpec() {
        return Specs.satisfyAll();
    }

    private ManagedSetSchemaExtractionStrategy(ModelType<T> type) {
        this.type = type;
    }

    public static ManagedSetSchemaExtractionStrategy<ManagedSet<?>> getInstance() {
        return new ManagedSetSchemaExtractionStrategy<ManagedSet<?>>(new ModelType<ManagedSet<?>>() {});
    }

    public <R extends T> ModelSchemaExtractionResult<R> extract(ModelType<R> type, ModelSchemaCache cache, ModelSchemaExtractionContext context) {
        List<ModelType<?>> typeVariables = type.getTypeVariables();

        if (typeVariables.isEmpty()) {
            throw new InvalidManagedModelElementTypeException(type, String.format("type parameter of %s has to be specified", ManagedSet.class.getName()), context);
        }
        if (type.isHasWildcardTypeVariables()) {
            throw new InvalidManagedModelElementTypeException(type, String.format("type parameter of %s cannot be a wildcard", ManagedSet.class.getName()), context);
        }
        if (MANAGED_SET_MODEL_TYPE.isAssignableFrom(typeVariables.get(0))) {
            throw new InvalidManagedModelElementTypeException(type, String.format("%1$s cannot be used as type parameter of %1$s", ManagedSet.class.getName()), context);
        }

        ModelSchema<R> schema = createSchema(type, cache);
        return new ModelSchemaExtractionResult<R>(schema, ImmutableList.of(new ManagedSetElementTypeExtractionContext(type, context)));
    }

    private <R extends T> ModelSchema<R> createSchema(ModelType<R> type, ModelSchemaCache cache) {
        ManagedSetInstantiator<R> elementInstantiator = new ManagedSetInstantiator<R>(cache);
        return new ModelSchema<R>(type, elementInstantiator);
    }

    private class ManagedSetInstantiator<S> implements Transformer<S, ModelSchema<S>> {

        private final ModelSchemaCache cache;

        ManagedSetInstantiator(ModelSchemaCache cache) {
            this.cache = cache;
        }

        public S transform(ModelSchema<S> schema) {
            ModelType<?> elementType = schema.getType().getTypeVariables().get(0);
            final ModelSchema<?> elementSchema = cache.get(elementType);
            return Cast.uncheckedCast(createManagedSetInstance(elementSchema));
        }

        private <E> DefaultManagedSet<E> createManagedSetInstance(final ModelSchema<E> elementSchema) {
            Factory<E> factory = new Factory<E>() {
                public E create() {
                    return elementSchema.createInstance();
                }
            };
            return new DefaultManagedSet<E>(factory);
        }
    }
}
