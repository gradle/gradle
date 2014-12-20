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

import java.util.List;

public class ManagedModelInitializer<T> implements BiAction<MutableModelNode, Inputs> {

    private final ModelSchema<T> modelSchema;
    private final BiAction<? super MutableModelNode, ? super Inputs> initializer;
    private final ManagedProxyFactory proxyFactory;
    private final ModelSchemaStore schemaStore;
    private final ModelInstantiator modelInstantiator;
    private final ModelRuleDescriptor descriptor;

    public static <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator modelInstantiator, ManagedProxyFactory proxyFactory, List<? extends ModelReference<?>> inputs, BiAction<? super MutableModelNode, ? super Inputs> initializer) {
        return ModelCreators.of(ModelReference.of(path, schema.getType()), new ManagedModelInitializer<T>(descriptor, schema, modelInstantiator, schemaStore, proxyFactory, initializer))
                .descriptor(descriptor)
                .withProjection(new ManagedModelProjection<T>(schema.getType(), schemaStore, proxyFactory))
                .inputs(inputs)
                .build();
    }

    public ManagedModelInitializer(ModelRuleDescriptor descriptor, ModelSchema<T> modelSchema, ModelInstantiator modelInstantiator, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, BiAction<? super MutableModelNode, ? super Inputs> initializer) {
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

        initializer.execute(modelNode, inputs);
    }

    private <P> void addPropertyLink(MutableModelNode modelNode, ModelProperty<P> property) {
        // TODO reuse pooled projections
        ModelType<P> propertyType = property.getType();
        ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);

        MutableModelNode childNode;

        if (propertySchema.getKind() == ModelSchema.Kind.STRUCT) {
            ModelProjection projection = new ManagedModelProjection<P>(propertyType, schemaStore, proxyFactory);
            childNode = modelNode.addLink(property.getName(), descriptor, projection, projection);

            if (!property.isWritable()) {
                for (ModelProperty<?> modelProperty : propertySchema.getProperties().values()) {
                    addPropertyLink(childNode, modelProperty);
                }
            }
        } else {
            ModelProjection projection = new UnmanagedModelProjection<P>(propertyType, true, true);
            childNode = modelNode.addLink(property.getName(), descriptor, projection, projection);

            if (propertySchema.getKind() == ModelSchema.Kind.COLLECTION) {
                P instance = modelInstantiator.newInstance(propertySchema);
                childNode.setPrivateData(propertyType, instance);
            }
        }
    }
}
