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
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class ManagedModelInitializer<T> implements BiAction<MutableModelNode, Inputs> {
    private static final BiAction<Object, Inputs> NO_OP = new BiAction<Object, Inputs>() {
        @Override
        public void execute(Object subject, Inputs inputs) {
        }
    };

    private final ModelSchema<T> modelSchema;
    private final ModelAction<T> initializer;
    private final ManagedProxyFactory proxyFactory;
    private final ModelSchemaStore schemaStore;
    private final ModelInstantiator modelInstantiator;
    private final ModelRuleDescriptor descriptor;

    public static <T> ModelCreator creator(final ModelRuleDescriptor descriptor,
                                           ModelPath path,
                                           final ModelSchema<T> schema,
                                           ModelSchemaStore schemaStore,
                                           final ModelInstantiator modelInstantiator,
                                           ManagedProxyFactory proxyFactory,
                                           final List<ModelReference<?>> inputs,
                                           final BiAction<? super T, ? super Inputs> initializer) {
        final ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        final ModelAction<T> modelAction = new InitializeAction<T>(modelReference, descriptor, inputs, initializer);
        if (schema.getKind() == ModelSchema.Kind.COLLECTION) {
            ModelProjection projection = ManagedSetModelProjection.of(schema.getType().getTypeVariables().get(0));
            return ModelCreators.of(modelReference, new ManagedSetInitializer<T>(modelInstantiator, schema, modelAction))
                    .withProjection(projection)
                    .descriptor(descriptor)
                    .inputs(inputs)
                    .build();

        }
        return ModelCreators.of(modelReference, new ManagedModelInitializer<T>(descriptor, schema, modelInstantiator, schemaStore, proxyFactory, modelAction))
                .descriptor(descriptor)
                .withProjection(new ManagedModelProjection<T>(schema.getType(), schemaStore, proxyFactory))
                .build();
    }

    public ManagedModelInitializer(ModelRuleDescriptor descriptor, ModelSchema<T> modelSchema, ModelInstantiator modelInstantiator, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, ModelAction<T> initializer) {
        this.descriptor = descriptor;
        this.modelInstantiator = modelInstantiator;
        this.schemaStore = schemaStore;
        this.modelSchema = modelSchema;
        this.initializer = initializer;
        this.proxyFactory = proxyFactory;
    }

    public void execute(MutableModelNode modelNode, Inputs inputs) {
        for (ModelProperty<?> property : modelSchema.getProperties().values()) {
            addPropertyLink(modelNode, property);
        }
        modelNode.applyToSelf(ModelActionRole.Initialize, initializer);
    }

    private <P> void addPropertyLink(MutableModelNode modelNode, ModelProperty<P> property) {
        // TODO reuse pooled projections
        ModelType<P> propertyType = property.getType();
        ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);

        if (propertySchema.getKind() == ModelSchema.Kind.STRUCT && !property.isWritable()) {
            ModelCreator creator = ManagedModelInitializer.creator(descriptor, modelNode.getPath().child(property.getName()), propertySchema, schemaStore, modelInstantiator, proxyFactory, Collections.<ModelReference<?>>emptyList(), NO_OP);
            modelNode.addLink(creator);
        } else if (propertySchema.getKind() == ModelSchema.Kind.COLLECTION) {
            ModelCreator creator = ManagedModelInitializer.creator(descriptor, modelNode.getPath().child(property.getName()), propertySchema, schemaStore, modelInstantiator, proxyFactory, Collections.<ModelReference<?>>emptyList(), NO_OP);
            modelNode.addLink(creator);
        } else {
            ModelProjection projection = new UnmanagedModelProjection<P>(propertyType, true, true);
            ModelCreator creator = ModelCreators.of(ModelReference.of(modelNode.getPath().child(property.getName()), propertyType), NO_OP)
                    .withProjection(projection)
                    .descriptor(descriptor).build();
            modelNode.addLink(creator);
        }
    }

    private static class InitializeAction<T> implements ModelAction<T> {
        private final ModelReference<T> modelReference;
        private final ModelRuleDescriptor descriptor;
        private final List<ModelReference<?>> inputs;
        private final BiAction<? super T, ? super Inputs> initializer;

        public InitializeAction(ModelReference<T> modelReference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, BiAction<? super T, ? super Inputs> initializer) {
            this.modelReference = modelReference;
            this.descriptor = descriptor;
            this.inputs = inputs;
            this.initializer = initializer;
        }

        @Override
        public ModelReference<T> getSubject() {
            return modelReference;
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        @Override
        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        @Override
        public void execute(MutableModelNode modelNode, T object, Inputs inputs) {
            initializer.execute(object, inputs);
        }
    }

    private static class ManagedSetInitializer<T> implements BiAction<MutableModelNode, Inputs> {
        private final ModelInstantiator modelInstantiator;
        private final ModelSchema<T> schema;
        private final ModelAction<T> modelAction;

        public ManagedSetInitializer(ModelInstantiator modelInstantiator, ModelSchema<T> schema, ModelAction<T> modelAction) {
            this.modelInstantiator = modelInstantiator;
            this.schema = schema;
            this.modelAction = modelAction;
        }

        @Override
        public void execute(MutableModelNode modelNode, Inputs inputs) {
            T instance = modelInstantiator.newInstance(schema);
            modelNode.setPrivateData(schema.getType(), instance);
            modelNode.applyToSelf(ModelActionRole.Initialize, modelAction);
        }
    }
}
