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
import org.gradle.api.Transformer;
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
import java.util.Set;

public class ManagedModelInitializer<T> implements Transformer<Action<ModelNode>, Inputs> {

    private final ModelSchema<T> modelSchema;
    private final BiAction<? super ModelView<? extends T>, ? super Inputs> initializer;
    private final ManagedProxyFactory proxyFactory;
    private final ModelSchemaStore schemaStore;
    private final ModelInstantiator modelInstantiator;
    private final ModelRuleDescriptor descriptor;

    public static <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator modelInstantiator, ManagedProxyFactory proxyFactory, List<? extends ModelReference<?>> inputs, BiAction<? super ModelView<? extends T>, ? super Inputs> initializer) {
        return ModelCreators.of(ModelReference.of(path, schema.getType()), new ManagedModelInitializer<T>(descriptor, schema, modelInstantiator, schemaStore, proxyFactory, initializer))
                .descriptor(descriptor)
                .withProjection(new ManagedModelProjection<T>(schema.getType(), schemaStore, proxyFactory))
                .inputs(inputs)
                .build();
    }

    public ManagedModelInitializer(ModelRuleDescriptor descriptor, ModelSchema<T> modelSchema, ModelInstantiator modelInstantiator, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, BiAction<? super ModelView<? extends T>, ? super Inputs> initializer) {
        this.descriptor = descriptor;
        this.modelInstantiator = modelInstantiator;
        this.schemaStore = schemaStore;
        this.modelSchema = modelSchema;
        this.initializer = initializer;
        this.proxyFactory = proxyFactory;
    }

    public Action<ModelNode> transform(final Inputs inputs) {
        return new Action<ModelNode>() {
            public void execute(ModelNode modelNode) {
                ModelView<? extends T> modelView = modelNode.getAdapter().asWritable(modelSchema.getType(), descriptor, inputs, modelNode);
                if (modelView == null) {
                    throw new IllegalStateException("Couldn't produce managed node as schema type");
                }

                for (ModelProperty<?> property : modelSchema.getProperties().values()) {
                    addPropertyLink(modelNode, property);
                }

                initializer.execute(modelView, inputs);

                modelView.close();
            }

            private <P> void addPropertyLink(ModelNode modelNode, ModelProperty<P> property) {
                // TODO reuse pooled projections/promises/adapters
                ModelType<P> propertyType = property.getType();
                ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);

                ModelNode childNode;

                if (propertySchema.getKind() == ModelSchema.Kind.STRUCT) {
                    Set<ModelProjection> projections = Collections.<ModelProjection>singleton(new ManagedModelProjection<P>(propertyType, schemaStore, proxyFactory));
                    ModelPromise promise = new ProjectionBackedModelPromise(projections);
                    ModelAdapter adapter = new ProjectionBackedModelAdapter(projections);
                    childNode = modelNode.addLink(property.getName(), descriptor, promise, adapter);

                    if (!property.isWritable()) {
                        for (ModelProperty<?> modelProperty : propertySchema.getProperties().values()) {
                            addPropertyLink(childNode, modelProperty);
                        }
                    }
                } else {
                    Set<ModelProjection> projections = Collections.<ModelProjection>singleton(new UnmanagedModelProjection<P>(propertyType, true, true));
                    ModelPromise promise = new ProjectionBackedModelPromise(projections);
                    ModelAdapter adapter = new ProjectionBackedModelAdapter(projections);
                    childNode = modelNode.addLink(property.getName(), descriptor, promise, adapter);

                    if (propertySchema.getKind() == ModelSchema.Kind.COLLECTION) {
                        P instance = modelInstantiator.newInstance(propertySchema);
                        childNode.setPrivateData(propertyType, instance);
                    }
                }

            }
        };
    }


}
