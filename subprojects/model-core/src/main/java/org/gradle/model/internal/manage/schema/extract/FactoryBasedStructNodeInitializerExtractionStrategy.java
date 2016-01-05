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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.InstanceFactory;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.inspect.FactoryBasedStructNodeInitializer;
import org.gradle.model.internal.manage.binding.StructBindings;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.schema.ManagedImplSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public class FactoryBasedStructNodeInitializerExtractionStrategy<T> implements NodeInitializerExtractionStrategy {
    protected final InstanceFactory<T> instanceFactory;
    private final ModelSchemaStore schemaStore;
    private final StructBindingsStore bindingsStore;

    public FactoryBasedStructNodeInitializerExtractionStrategy(InstanceFactory<T> instanceFactory, ModelSchemaStore schemaStore, StructBindingsStore bindingsStore) {
        this.instanceFactory = instanceFactory;
        this.schemaStore = schemaStore;
        this.bindingsStore = bindingsStore;
    }

    @Override
    public <S> NodeInitializer extractNodeInitializer(ModelSchema<S> schema) {
        if (!instanceFactory.getBaseInterface().isAssignableFrom(schema.getType())) {
            return null;
        }
        return getNodeInitializer(Cast.<ModelSchema<? extends T>>uncheckedCast(schema));
    }

    private <S extends T> NodeInitializer getNodeInitializer(final ModelSchema<S> schema) {
        StructSchema<S> publicSchema = Cast.uncheckedCast(schema);
        InstanceFactory.ImplementationInfo<T> implementationInfo;
        ModelType<S> publicType = schema.getType();
        if (publicSchema instanceof ManagedImplSchema) {
            implementationInfo = instanceFactory.getManagedSubtypeImplementationInfo(publicType);
        } else {
            implementationInfo = instanceFactory.getImplementationInfo(publicType);
        }
        Set<ModelType<?>> internalViews = instanceFactory.getInternalViews(publicType);
        ModelType<? extends T> delegateType = implementationInfo.getDelegateType();
        StructBindings<S> bindings = bindingsStore.getBindings(publicSchema, getSchemas(internalViews), getSchema(delegateType));
        return new FactoryBasedStructNodeInitializer<T, S>(bindings, implementationInfo);
    }

    private Iterable<StructSchema<?>> getSchemas(Iterable<ModelType<?>> types) {
        return Iterables.transform(types, new Function<ModelType<?>, StructSchema<?>>() {
            @Override
            public StructSchema<?> apply(ModelType<?> type) {
                return getSchema(type);
            }
        });
    }

    private <S> StructSchema<S> getSchema(ModelType<S> type) {
        if (type == null) {
            return null;
        }
        ModelSchema<S> schema = schemaStore.getSchema(type);
        if (!(schema instanceof StructSchema)) {
            throw new IllegalArgumentException("Not a struct type: " + type);
        }
        return Cast.uncheckedCast(schema);
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.<ModelType<?>>copyOf(instanceFactory.getSupportedTypes());
    }
}
