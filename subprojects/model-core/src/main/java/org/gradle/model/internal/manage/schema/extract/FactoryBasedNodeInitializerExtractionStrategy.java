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
import org.gradle.api.Action;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.FactoryBasedManagedNodeInitializer;
import org.gradle.model.internal.core.FactoryBasedNodeInitializer;
import org.gradle.model.internal.core.InstanceFactory;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelManagedImplStructSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

public class FactoryBasedNodeInitializerExtractionStrategy<T> implements NodeInitializerExtractionStrategy {
    private final InstanceFactory<T> instanceFactory;
    private final ModelSchemaStore schemaStore;
    private final ManagedProxyFactory proxyFactory;
    private final BiAction<? super T, ? super ModelSchema<? extends T>> configAction;

    public FactoryBasedNodeInitializerExtractionStrategy(InstanceFactory<T> instanceFactory, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, BiAction<? super T, ? super ModelSchema<? extends T>> configAction) {
        this.instanceFactory = instanceFactory;
        this.schemaStore = schemaStore;
        this.proxyFactory = proxyFactory;
        this.configAction = configAction;
    }

    @Override
    public <S> NodeInitializer extractNodeInitializer(ModelSchema<S> schema) {
        if (!instanceFactory.getBaseInterface().isAssignableFrom(schema.getType())) {
            return null;
        }
        return getNodeInitializer(Cast.<ModelSchema<? extends T>>uncheckedCast(schema));
    }

    private <S extends T> NodeInitializer getNodeInitializer(final ModelSchema<S> schema) {
        if (schema instanceof ModelManagedImplStructSchema) {
            ModelManagedImplStructSchema<S> managedSchema = Cast.uncheckedCast(schema);
            return new FactoryBasedManagedNodeInitializer<T, S>(instanceFactory, managedSchema, schemaStore, proxyFactory, new Action<T>() {
                @Override
                public void execute(T instance) {
                    configAction.execute(instance, schema);
                }
            });
        } else {
            return new FactoryBasedNodeInitializer<T, S>(instanceFactory, schema.getType());
        }
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.<ModelType<?>>copyOf(instanceFactory.getSupportedTypes());
    }
}
