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
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.TypeCompatibilityModelProjectionSupport;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.binding.StructBindings;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.manage.schema.extract.ScalarCollectionModelView;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.reflect.JavaReflectionUtil.hasDefaultToString;

public class ManagedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    private static final ModelType<? extends Collection<?>> COLLECTION_MODEL_TYPE = new ModelType<Collection<?>>() {
    };
    private final StructSchema<M> schema;
    private final StructBindings<?> bindings;
    private final ManagedProxyFactory proxyFactory;
    private final TypeConverter typeConverter;

    public ManagedModelProjection(StructSchema<M> schema,
                                  StructBindings<?> bindings,
                                  ManagedProxyFactory proxyFactory,
                                  TypeConverter typeConverter) {
        super(schema.getType());
        this.schema = schema;
        this.bindings = bindings;
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
                return proxyFactory.createProxy(new State(), schema, bindings, typeConverter);
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

                @Override
                public boolean equals(Object obj) {
                    if (obj == this) {
                        return true;
                    }
                    if (obj == null || obj.getClass() != getClass()) {
                        return false;
                    }

                    State other = Cast.uncheckedCast(obj);
                    return modelNode == other.getBackingNode();
                }

                @Override
                public int hashCode() {
                    return modelNode.hashCode();
                }

                @Override
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
                    if (writable) {
                        modelView = propertyNode.asMutable(propertyType, ruleDescriptor);
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

                @Override
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
                        if (propertySchema instanceof ScalarCollectionSchema) {
                            ModelView<? extends Collection<?>> modelView = propertyNode.asMutable(COLLECTION_MODEL_TYPE, ruleDescriptor);
                            return ((ScalarCollectionModelView<?, ? extends Collection<?>>) modelView).setValue(value);
                        } else if (value == null) {
                            propertyNode.setTarget(null);
                        } else if (value instanceof ManagedInstance) {
                            ManagedInstance managedInstance = (ManagedInstance) value;
                            MutableModelNode targetNode = managedInstance.getBackingNode();
                            propertyNode.setTarget(targetNode);
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
    public Optional<String> getValueDescription(MutableModelNode modelNode) {
        Object instance = modelNode.asImmutable(ModelType.untyped(), null).getInstance();
        if (instance == null || hasDefaultToString(instance)) {
            return Optional.absent();
        }
        return Optional.of(toStringValueDescription(instance));
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
