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

package org.gradle.model.internal.core;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.AbstractManagedModelInitializer;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FactoryBasedNodeInitializer<T, S extends T> extends AbstractManagedModelInitializer<S> implements NodeInitializer {
    private final InstanceFactory<T> instanceFactory;
    private final Action<? super T> configureAction;

    public FactoryBasedNodeInitializer(InstanceFactory<T> instanceFactory, StructSchema<S> modelSchema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, ServiceRegistry services, Action<? super T> configureAction) {
        super(modelSchema, schemaStore, proxyFactory, services);
        this.instanceFactory = instanceFactory;
        this.configureAction = configureAction;
    }

    @Override
    public List<? extends ModelReference<?>> getInputs() {
        return Collections.singletonList(ModelReference.of(NodeInitializerRegistry.class));
    }

    @Override
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        ModelType<S> type = schema.getType();
        ModelType<? extends T> publicType;
        ModelType<T> delegateType;
        if (schema instanceof ManagedImplSchema) {
            InstanceFactory.ManagedSubtypeImplementationInfo<? extends T> implementationInfo = instanceFactory.getManagedSubtypeImplementationInfo(type);
            publicType = implementationInfo.getPublicType();
            delegateType = Cast.uncheckedCast(implementationInfo.getDelegateType());
        } else {
            publicType = type;
            delegateType = Cast.uncheckedCast(instanceFactory.getImplementationType(publicType));
        }
        T instance = instanceFactory.create(publicType, modelNode, modelNode.getPath().getName());
        configureAction.execute(instance);
        modelNode.setPrivateData(delegateType, instance);

        NodeInitializerRegistry nodeInitializerRegistry = ModelViews.assertType(inputs.get(0), NodeInitializerRegistry.class).getInstance();
        StructSchema<T> delegateSchema = Cast.uncheckedCast(schemaStore.getSchema(delegateType));
        addPropertyLinks(modelNode, nodeInitializerRegistry, getProperties(delegateSchema));
    }

    private Collection<ModelProperty<?>> getProperties(StructSchema<T> delegateSchema) {
        ImmutableSet.Builder<ModelProperty<?>> properties = ImmutableSet.builder();
        addNonDelegatedManagedProperties(schema, delegateSchema, properties);
        for (ModelType<?> internalView : instanceFactory.getInternalViews(schema.getType())) {
            ModelSchema<?> internalViewSchema = schemaStore.getSchema(internalView);
            if (!(internalViewSchema instanceof ManagedImplStructSchema)) {
                continue;
            }
            addNonDelegatedManagedProperties((ManagedImplStructSchema<?>) internalViewSchema, delegateSchema, properties);
        }
        return properties.build();
    }

    private void addNonDelegatedManagedProperties(StructSchema<?> schema, StructSchema<T> delegateSchema, ImmutableSet.Builder<ModelProperty<?>> properties) {
        for (ModelProperty<?> property : schema.getProperties()) {
            if (property.getStateManagementType() != ModelProperty.StateManagementType.MANAGED) {
                continue;
            }
            if (delegateSchema.hasProperty(property.getName())) {
                continue;
            }
            properties.add(property);
        }
    }

    @Override
    public List<? extends ModelProjection> getProjections() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public ModelAction getProjector(final ModelPath path, final ModelRuleDescriptor descriptor) {
        return new AbstractModelAction<Object>(ModelReference.of(path), descriptor, ModelReference.of(ModelSchemaStore.class), ModelReference.of(ManagedProxyFactory.class)) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                ModelSchemaStore schemaStore = ModelViews.getInstance(inputs.get(0), ModelSchemaStore.class);
                ManagedProxyFactory proxyFactory = ModelViews.getInstance(inputs.get(1), ManagedProxyFactory.class);

                ModelType<S> publicType = schema.getType();
                ModelType<? extends T> delegateType;
                if (schema instanceof ManagedImplSchema) {
                    InstanceFactory.ManagedSubtypeImplementationInfo<? extends T> implementationInfo = instanceFactory.getManagedSubtypeImplementationInfo(publicType);
                    if (implementationInfo == null) {
                        throw new IllegalStateException(String.format("No default implementation registered for managed type '%s'", publicType));
                    }
                    delegateType = implementationInfo.getDelegateType();
                } else {
                    delegateType = instanceFactory.getImplementationType(publicType);
                }
                ModelSchema<? extends T> delegateSchema = schemaStore.getSchema(delegateType);
                if (!(delegateSchema instanceof StructSchema)) {
                    throw new IllegalStateException(String.format("Default implementation '%s' registered for managed type '%s' must be a struct",
                        delegateType, publicType));
                }
                StructSchema<? extends T> delegateStructSchema = Cast.uncheckedCast(delegateSchema);

                addProjection(modelNode, publicType, delegateStructSchema, schemaStore, proxyFactory);
                for (ModelType<?> internalView : instanceFactory.getInternalViews(publicType)) {
                    addProjection(modelNode, internalView, delegateStructSchema, schemaStore, proxyFactory);
                }
            }

            private <D> void addProjection(MutableModelNode modelNode, ModelType<?> type, StructSchema<? extends D> delegateSchema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory) {
                ModelSchema<?> schema = schemaStore.getSchema(type);
                if (!(schema instanceof StructSchema)) {
                    throw new IllegalStateException("View type must be a struct: " + type);
                }
                StructSchema<D> structSchema = Cast.uncheckedCast(schema);
                ModelProjection projection;
                if (structSchema instanceof ManagedImplSchema) {
                    projection = new ManagedModelProjection<D>(structSchema, delegateSchema, schemaStore, proxyFactory, services);
                } else {
                    projection = UnmanagedModelProjection.of(structSchema.getType());
                }
                modelNode.addProjection(projection);
            }
        };
    }
}
