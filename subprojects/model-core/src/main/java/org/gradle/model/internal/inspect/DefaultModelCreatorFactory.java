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

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.projection.ManagedSetModelProjection;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelStructSchema;

import java.util.List;

public class DefaultModelCreatorFactory implements ModelCreatorFactory {
    private final ModelSchemaStore schemaStore;
    private final ManagedProxyFactory proxyFactory;

    public DefaultModelCreatorFactory(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
        this.proxyFactory = new ManagedProxyFactory();
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        return creator(descriptor, modelReference, schema, null);
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, Action<? super T> initializer) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        ModelAction<T> modelAction = new ActionBackedModelAction<T>(modelReference, descriptor, initializer);
        return creator(descriptor, modelReference, schema, modelAction);
    }

    @Override
    public <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, List<ModelReference<?>> initializerInputs, BiAction<? super T, ? super List<ModelView<?>>> initializer) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        ModelAction<T> modelAction = new BiActionBackedModelAction<T>(modelReference, descriptor, initializerInputs, initializer);
        return creator(descriptor, modelReference, schema, modelAction);
    }

    private <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelReference<T> modelReference, ModelSchema<T> schema, @Nullable ModelAction<T> initializer) {
        // TODO reuse pooled projections
        if (schema instanceof ModelCollectionSchema) {
            ModelCollectionSchema<T> collectionSchema = (ModelCollectionSchema<T>) schema;
            ModelSchema<?> elementSchema = schemaStore.getSchema(collectionSchema.getElementType());
            return ModelCreators.of(modelReference, new ManagedSetInitializer<T>(initializer))
                    .withProjection(ManagedSetModelProjection.of(elementSchema, this))
                    .descriptor(descriptor)
                    .build();
        }
        if (schema instanceof ModelStructSchema) {
            ModelStructSchema<T> structSchema = (ModelStructSchema<T>) schema;
            return ModelCreators.of(modelReference, new ManagedModelInitializer<T>(descriptor, structSchema, schemaStore, this, initializer))
                    .withProjection(new ManagedModelProjection<T>(structSchema, schemaStore, proxyFactory))
                    .descriptor(descriptor)
                    .build();
        }
        throw new IllegalArgumentException("Don't know how to create model element from schema for " + schema.getType());
    }
}
