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

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.AbstractManagedModelInitializer;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.ModelManagedImplStructSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class FactoryBasedManagedNodeInitializer<T, S extends T> extends AbstractManagedModelInitializer<S> implements NodeInitializer {
    private final InstanceFactory<T> instanceFactory;
    private final Action<? super T> configureAction;

    public FactoryBasedManagedNodeInitializer(InstanceFactory<T> instanceFactory, ModelManagedImplStructSchema<S> modelSchema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, Action<? super T> configureAction) {
        super(modelSchema, schemaStore, proxyFactory);
        this.instanceFactory = instanceFactory;
        this.configureAction = configureAction;
    }

    @Override
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        ModelType<S> type = schema.getType();
        InstanceFactory.ImplementationInfo<? extends T> implementationInfo = instanceFactory.getImplementationInfo(type);
        T instance = instanceFactory.create(implementationInfo.getPublicType(), modelNode, modelNode.getPath().getName());
        configureAction.execute(instance);
        ModelType<T> delegateType = Cast.uncheckedCast(implementationInfo.getDelegateType());
        modelNode.setPrivateData(delegateType, instance);
        super.execute(modelNode, inputs);
    }

    @Override
    public List<? extends ModelProjection> getProjections() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public ModelAction getProjector(final ModelPath path, final ModelRuleDescriptor descriptor) {
        return new AbstractModelAction<Object>(ModelReference.of(path), descriptor, ModelReference.of("schemaStore", ModelSchemaStore.class), ModelReference.of(ManagedProxyFactory.class)) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                ModelSchemaStore schemaStore = ModelViews.getInstance(inputs.get(0), ModelSchemaStore.class);
                ManagedProxyFactory proxyFactory = ModelViews.getInstance(inputs.get(1), ManagedProxyFactory.class);

                ModelType<S> managedType = schema.getType();
                InstanceFactory.ImplementationInfo<? extends T> implementationInfo = instanceFactory.getImplementationInfo(managedType);
                if (implementationInfo == null) {
                    throw new IllegalStateException(String.format("No default implementation registered for managed type '%s'", managedType));
                }
                ModelType<? extends T> delegateType = implementationInfo.getDelegateType();
                ModelSchema<? extends T> delegateSchema = schemaStore.getSchema(delegateType);
                if (!(delegateSchema instanceof ModelStructSchema)) {
                    throw new IllegalStateException(String.format("Default implementation '%s' registered for managed type '%s' must be a struct", delegateType, managedType));
                }
                ModelStructSchema<? extends T> delegateStructSchema = Cast.uncheckedCast(delegateSchema);

                // Register managed projections
                addManagedProjection(modelNode, managedType, delegateStructSchema, schemaStore, proxyFactory);

                // TODO:LPTR get all internal views (managed and unmanaged) in one go, including internal views defined for super-types
                // Register managed internal views
                for (ModelType<?> internalView : instanceFactory.getInternalViews(managedType)) {
                    addManagedProjection(modelNode, internalView, delegateStructSchema, schemaStore, proxyFactory);
                }

                // Register unmanaged internal views registered for the public type
                for (ModelType<?> internalView : instanceFactory.getInternalViews(implementationInfo.getPublicType())) {
                    modelNode.addProjection(UnmanagedModelProjection.of(internalView));
                }
            }

            private <D> void addManagedProjection(MutableModelNode modelNode, ModelType<?> type, ModelStructSchema<? extends D> delegateSchema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory) {
                ModelSchema<?> schema = schemaStore.getSchema(type);
                if (!(schema instanceof ModelStructSchema)) {
                    throw new IllegalStateException("View type must be a struct: " + type);
                }
                ModelManagedImplStructSchema<D> structSchema = Cast.uncheckedCast(schema);
                modelNode.addProjection(new ManagedModelProjection<D>(structSchema, delegateSchema, schemaStore, proxyFactory));
            }
        };
    }
}
