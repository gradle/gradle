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

package org.gradle.model.internal.inspect;

import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.instance.strategy.StrategyBackedModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

import java.util.List;

public class DefaultModelCreatorFactory implements ModelCreatorFactory {
    private final ModelSchemaStore schemaStore;
    private final ModelInstantiator modelInstantiator;
    private final ManagedProxyFactory proxyFactory;

    public DefaultModelCreatorFactory(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
        this.proxyFactory = new ManagedProxyFactory();
        this.modelInstantiator = new StrategyBackedModelInstantiator(schemaStore, proxyFactory);
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, List<ModelReference<?>> inputs, BiAction<? super T, ? super Inputs> initializer) {
        final ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        final ModelAction<T> modelAction = new BiActionBackedModelAction<T>(modelReference, descriptor, inputs, initializer);
        if (schema.getKind() == ModelSchema.Kind.COLLECTION) {
            ModelProjection projection = ManagedSetModelProjection.of(schema.getType().getTypeVariables().get(0));
            return ModelCreators.of(modelReference, new ManagedSetInitializer<T>(modelInstantiator, schema, modelAction))
                    .withProjection(projection)
                    .descriptor(descriptor)
                    .inputs(inputs)
                    .build();

        }
        if (schema.getKind() == ModelSchema.Kind.STRUCT) {
            return ModelCreators.of(modelReference, new ManagedModelInitializer<T>(descriptor, schema, schemaStore, this, modelAction))
                    .descriptor(descriptor)
                    .withProjection(new ManagedModelProjection<T>(schema.getType(), schemaStore, proxyFactory))
                    .build();
        }
        throw new IllegalArgumentException("Don't know how to create model element from schema for " + schema.getType());
    }
}
