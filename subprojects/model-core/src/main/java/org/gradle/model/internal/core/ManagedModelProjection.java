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

package org.gradle.model.internal.core;

import org.gradle.internal.Cast;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

public class ManagedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    private final ModelSchemaStore schemaStore;
    private final ManagedProxyFactory proxyFactory;
    private final ModelSchema<M> schema;

    public ManagedModelProjection(ModelType<M> type, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory) {
        super(type, true, true);
        this.schema = schemaStore.getSchema(type);
        this.schemaStore = schemaStore;
        this.proxyFactory = proxyFactory;
    }

    @Override
    protected ModelView<M> toView(final MutableModelNode modelNode, final ModelRuleDescriptor ruleDescriptor, final boolean writable) {
        return new ModelView<M>() {

            private boolean closed;

            public ModelType<M> getType() {
                return ManagedModelProjection.this.getType();
            }

            public M getInstance() {
                return proxyFactory.createProxy(new State(), schema);
            }

            public void close() {
                closed = true;
            }

            // TODO we are relying on the creator having established these links, we should be checking
            class State implements ModelElementState {
                public Object get(String name) {
                    ModelProperty<?> property = schema.getProperties().get(name);
                    ModelType<?> propertyType = property.getType();

                    return doGet(propertyType, name, property);
                }

                private <T> T doGet(ModelType<T> propertyType, String propertyName, ModelProperty<?> property) {
                    MutableModelNode propertyNode = modelNode.getLink(propertyName);

                    if (!property.isWritable()) {
                        // TODO we are creating a new object each time the getter is called - we should reuse the instance for the life of the view
                        if (writable) {
                            ModelView<? extends T> modelView = propertyNode.asWritable(propertyType, ruleDescriptor, null);
                            if (closed) {
                                //noinspection ConstantConditions
                                modelView.close();
                            }
                            //noinspection ConstantConditions
                            return modelView.getInstance();
                        } else {
                            //noinspection ConstantConditions
                            return propertyNode.asReadOnly(propertyType, ruleDescriptor).getInstance();
                        }
                    } else {
                        return propertyNode.getPrivateData(propertyType);
                    }
                }

                public void set(String name, Object value) {
                    if (!writable || closed) {
                        throw new ModelViewClosedException(getType(), ruleDescriptor);
                    }

                    ModelProperty<?> property = schema.getProperties().get(name);
                    ModelType<?> propertyType = property.getType();

                    doSet(name, value, propertyType);
                }

                private <T> void doSet(String name, Object value, ModelType<T> propertyType) {
                    ModelSchema<T> schema = schemaStore.getSchema(propertyType);

                    if (schema.getKind().isManaged() && !ManagedInstance.class.isInstance(value)) {
                        throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", name, getType()));
                    }
                    T castValue = Cast.uncheckedCast(value);
                    modelNode.getLink(name).setPrivateData(propertyType, castValue);
                }
            }
        };
    }


}
