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

import com.google.common.base.Optional;
import groovy.lang.Closure;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.Cast;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ManagedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    private static final ModelType<? extends Collection<?>> COLLECTION_MODEL_TYPE = new ModelType<Collection<?>>() {
    };
    private final StructSchema<M> schema;
    private final StructSchema<? extends M> delegateSchema;
    private final ManagedProxyFactory proxyFactory;
    private final TypeConverter typeConverter;

    public ManagedModelProjection(StructSchema<M> schema, StructSchema<? extends M> delegateSchema,
                                  ManagedProxyFactory proxyFactory,
                                  TypeConverter typeConverter) {
        super(schema.getType(), true, true);
        this.schema = schema;
        this.delegateSchema = delegateSchema;
        this.proxyFactory = proxyFactory;
        this.typeConverter = typeConverter;
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
                return proxyFactory.createProxy(new State(), schema, delegateSchema, typeConverter);
            }

            public void close() {
                closed = true;
            }

            class State implements ModelElementState {

                State() {
                }

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

                    ModelProperty<?> property = schema.getProperty(name);

                    Object value = doGet(property, name);
                    propertyViews.put(name, value);
                    return value;
                }

                private <T> T doGet(ModelProperty<T> property, String propertyName) {
                    ModelType<T> propertyType = property.getType();

                    // TODO we are relying on the registration having established these links, we should be checking
                    MutableModelNode propertyNode = modelNode.getLink(propertyName);
                    propertyNode.ensureUsable();

                    ModelView<? extends T> modelView;
                    ModelSchema<T> propertySchema = property.getSchema();
                    if (property.isWritable() && propertySchema instanceof ScalarCollectionSchema) {
                        Collection<?> instance = ScalarCollectionSchema.get(propertyNode);
                        if (instance == null) {
                            return null;
                        }
                    }
                    if (writable) {
                        modelView = propertyNode.asMutable(propertyType, ruleDescriptor, null);
                        if (closed) {
                            modelView.close();
                        }
                    } else {
                        modelView = propertyNode.asImmutable(propertyType, ruleDescriptor);
                    }
                    return modelView.getInstance();
                }

                @Override
                public void apply(String name, Closure<?> action) {
                    ClosureBackedAction.execute(get(name), action);
                }

                public void set(String name, Object value) {
                    if (!writable || closed) {
                        throw new ModelViewClosedException(getType(), ruleDescriptor);
                    }

                    ModelProperty<?> property = schema.getProperty(name);

                    value = doSet(name, value, property);
                    propertyViews.put(name, value);
                }

                private <T> Object doSet(String name, Object value, ModelProperty<T> property) {
                    ModelSchema<T> propertySchema = property.getSchema();

                    // TODO we are relying on the registration having established these links, we should be checking
                    MutableModelNode propertyNode = modelNode.getLink(name);
                    propertyNode.ensureUsable();

                    if (propertySchema instanceof ManagedImplSchema) {
                        if (value == null) {
                            if (propertySchema instanceof ScalarCollectionSchema) {
                                ScalarCollectionSchema.clear(propertyNode);
                            } else {
                                propertyNode.setTarget(null);
                            }
                        } else if (ManagedInstance.class.isInstance(value)) {
                            ManagedInstance managedInstance = (ManagedInstance) value;
                            MutableModelNode targetNode = managedInstance.getBackingNode();
                            propertyNode.setTarget(targetNode);
                        } else if (propertySchema instanceof ScalarCollectionSchema && value instanceof Collection) {
                            ModelView<? extends Collection<?>> modelView = propertyNode.asMutable(COLLECTION_MODEL_TYPE, ruleDescriptor, Collections.<ModelView<?>>emptyList());
                            Collection<Object> instance = Cast.uncheckedCast(modelView.getInstance());
                            Collection<Object> values = Cast.uncheckedCast(value);
                            instance.clear();
                            instance.addAll(values);
                            return instance;
                        } else {
                            throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", name, getType()));
                        }
                    } else {
                        T castValue = Cast.uncheckedCast(value);
                        propertyNode.setPrivateData(property.getType(), castValue);
                    }
                    return value;
                }
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        return this == o || !(o == null || getClass() != o.getClass()) && super.equals(o);
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }


    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
