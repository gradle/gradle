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

package org.gradle.model.internal.manage.projection;

import org.gradle.internal.Cast;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.TypeCompatibilityModelProjectionSupport;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.HashMap;
import java.util.Map;

public class ManagedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    private final ModelSchemaStore schemaStore;
    private final ManagedProxyFactory proxyFactory;
    private final ModelStructSchema<M> schema;

    public ManagedModelProjection(ModelStructSchema<M> schema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory) {
        super(schema.getType(), true, true);
        this.schema = schema;
        this.schemaStore = schemaStore;
        this.proxyFactory = proxyFactory;
    }

    @Override
    protected ModelView<M> toView(final MutableModelNode modelNode, final ModelRuleDescriptor ruleDescriptor, final boolean writable) {
        return new ModelView<M>() {

            private boolean closed;
            private final Map<String, Object> propertyViews = new HashMap<String, Object>();

            @Override
            public ModelPath getPath() {
                return modelNode.getPath();
            }

            public ModelType<M> getType() {
                return ManagedModelProjection.this.getType();
            }

            public M getInstance() {
                return proxyFactory.createProxy(new State(), schema);
            }

            public void close() {
                closed = true;
            }

            class State implements ModelElementState {
                @Override
                public MutableModelNode getBackingNode() {
                    return modelNode;
                }

                @Override
                public String getDisplayName() {
                    return String.format("%s '%s'", getType(), modelNode.getPath().toString());
                }

                public Object get(String name) {
                    if (propertyViews.containsKey(name)) {
                        return propertyViews.get(name);
                    }

                    ModelProperty<?> property = schema.getProperties().get(name);

                    Object value = doGet(property, name);
                    propertyViews.put(name, value);
                    return value;
                }

                private <T> T doGet(ModelProperty<T> property, String propertyName) {
                    ModelType<T> propertyType = property.getType();
                    ModelSchema<T> schema = schemaStore.getSchema(propertyType);

                    // TODO we are relying on the creator having established these links, we should be checking
                    MutableModelNode propertyNode = modelNode.getLink(propertyName);
                    propertyNode.ensureUsable();

                    MutableModelNode targetNode = propertyNode;
                    if (property.isWritable() && schema.getKind().isManaged()) {
                        targetNode = propertyNode.getTarget();
                        if (targetNode == null) {
                            return null;
                        }
                    }

                    if (writable) {
                        ModelView<? extends T> modelView = targetNode.asWritable(propertyType, ruleDescriptor, null);
                        if (closed) {
                            modelView.close();
                        }
                        return modelView.getInstance();
                    } else {
                        return targetNode.asReadOnly(propertyType, ruleDescriptor).getInstance();
                    }
                }

                public void set(String name, Object value) {
                    if (!writable || closed) {
                        throw new ModelViewClosedException(getType(), ruleDescriptor);
                    }

                    ModelProperty<?> property = schema.getProperties().get(name);
                    ModelType<?> propertyType = property.getType();

                    doSet(name, value, propertyType);
                    propertyViews.put(name, value);
                }

                private <T> void doSet(String name, Object value, ModelType<T> propertyType) {
                    ModelSchema<T> schema = schemaStore.getSchema(propertyType);

                    // TODO we are relying on the creator having established these links, we should be checking
                    MutableModelNode propertyNode = modelNode.getLink(name);
                    propertyNode.ensureUsable();

                    if (schema.getKind().isManaged()) {
                        if (value == null) {
                            propertyNode.setTarget(null);
                        } else if (ManagedInstance.class.isInstance(value)) {
                            ManagedInstance managedInstance = (ManagedInstance) value;
                            MutableModelNode targetNode = managedInstance.getBackingNode();
                            propertyNode.setTarget(targetNode);
                        } else {
                            throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", name, getType()));
                        }
                    } else {
                        T castValue = Cast.uncheckedCast(value);
                        propertyNode.setPrivateData(propertyType, castValue);
                    }
                }
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        return this == o || !(o == null || getClass() != o.getClass()) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
