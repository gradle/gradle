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

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.InstanceFactory;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerContext;
import org.gradle.model.internal.inspect.FactoryBasedStructNodeInitializer;
import org.gradle.model.internal.manage.binding.StructBindings;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.schema.ManagedImplSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public class FactoryBasedStructNodeInitializerExtractionStrategy<T> implements NodeInitializerExtractionStrategy {
    private final InstanceFactory<T> instanceFactory;
    private final StructBindingsStore bindingsStore;

    public FactoryBasedStructNodeInitializerExtractionStrategy(InstanceFactory<T> instanceFactory, StructBindingsStore bindingsStore) {
        this.instanceFactory = instanceFactory;
        this.bindingsStore = bindingsStore;
    }

    @Override
    public <S> NodeInitializer extractNodeInitializer(ModelSchema<S> schema, NodeInitializerContext<S> context) {
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
        StructBindings<S> bindings = bindingsStore.getBindings(publicSchema.getType(), internalViews, delegateType);
        return new FactoryBasedStructNodeInitializer<T, S>(bindings, implementationInfo);
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.<ModelType<?>>copyOf(instanceFactory.getSupportedTypes());
    }
}
