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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.AbstractManagedModelInitializer;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.ManagedModelProjection;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class FactoryBasedNodeInitializer<T, S extends T> extends AbstractManagedModelInitializer<S> {
    private final InstanceFactory<T> instanceFactory;

    public FactoryBasedNodeInitializer(InstanceFactory<T> instanceFactory, StructSchema<S> modelSchema) {
        super(modelSchema);
        this.instanceFactory = instanceFactory;
    }

    @Override
    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
            .put(ModelActionRole.Discover, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(ModelSchemaStore.class),
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        ModelSchemaStore schemaStore = ModelViews.getInstance(modelViews, 0, ModelSchemaStore.class);
                        ManagedProxyFactory proxyFactory = ModelViews.getInstance(modelViews, 1, ManagedProxyFactory.class);
                        TypeConverter typeConverter = ModelViews.getInstance(modelViews, 2, TypeConverter.class);

                        ModelType<S> publicType = schema.getType();
                        ModelType<? extends T> delegateType = delegateTypeFor(publicType);
                        ModelSchema<? extends T> delegateSchema = schemaStore.getSchema(delegateType);
                        if (!(delegateSchema instanceof StructSchema)) {
                            throw new IllegalStateException(String.format("Default implementation '%s' registered for managed type '%s' must be a struct",
                                delegateType, publicType));
                        }
                        StructSchema<? extends T> delegateStructSchema = Cast.uncheckedCast(delegateSchema);
                        addProjection(modelNode, publicType, delegateStructSchema, schemaStore, proxyFactory, typeConverter);
                        addInternalViewProjections(modelNode, schemaStore, proxyFactory, typeConverter, publicType, delegateStructSchema);
                    }
                }
            ))
            .put(ModelActionRole.Create, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(NodeInitializerRegistry.class),
                    ModelReference.of(ModelSchemaStore.class),
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        NodeInitializerRegistry nodeInitializerRegistry = ModelViews.getInstance(modelViews, 0, NodeInitializerRegistry.class);
                        ModelSchemaStore schemaStore = ModelViews.getInstance(modelViews, 1, ModelSchemaStore.class);
                        ManagedProxyFactory proxyFactory = ModelViews.getInstance(modelViews, 2, ManagedProxyFactory.class);
                        TypeConverter typeConverter = ModelViews.getInstance(modelViews, 3, TypeConverter.class);

                        ModelType<S> type = schema.getType();
                        ModelType<T> delegateType;
                        InstanceFactory.ImplementationInfo<? extends T> implementationInfo;
                        if (schema instanceof ManagedImplSchema) {
                            implementationInfo = instanceFactory.getManagedSubtypeImplementationInfo(type);
                        } else {
                            implementationInfo = instanceFactory.getImplementationInfo(type);
                        }
                        delegateType = Cast.uncheckedCast(implementationInfo.getDelegateType());
                        T instance = implementationInfo.create(modelNode);
                        modelNode.setPrivateData(delegateType, instance);

                        StructSchema<T> delegateSchema = Cast.uncheckedCast(schemaStore.getSchema(delegateType));
                        addPropertyLinks(modelNode, nodeInitializerRegistry, proxyFactory, getProperties(delegateSchema, schemaStore), typeConverter);
                        hideNodesOfHiddenProperties(modelNode, getHiddenProperties(delegateSchema, schemaStore));
                    }
                }
            ))
            .build();
    }

    private void addInternalViewProjections(MutableModelNode modelNode, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, TypeConverter typeConverter, ModelType<S> publicType, StructSchema<? extends T> delegateStructSchema) {
        for (ModelType<?> internalView : internalViewsFor(publicType)) {
            addProjection(modelNode, internalView, delegateStructSchema, schemaStore, proxyFactory, typeConverter);
        }
    }

    private Set<ModelType<?>> internalViewsFor(ModelType<S> publicType) {
        return instanceFactory.getInternalViews(publicType);
    }

    private ModelType<? extends T> delegateTypeFor(ModelType<S> publicType) {
        if (schema instanceof ManagedImplSchema) {
            return instanceFactory.getManagedSubtypeImplementationInfo(publicType).getDelegateType();
        } else {
            return instanceFactory.getImplementationInfo(publicType).getDelegateType();
        }
    }

    private Collection<ModelProperty<?>> getProperties(StructSchema<T> delegateSchema, ModelSchemaStore schemaStore) {
        ImmutableSet.Builder<ModelProperty<?>> properties = ImmutableSet.builder();
        addNonDelegatedManagedProperties(schema, delegateSchema, properties);
        addInternalViewsProperties(delegateSchema, schemaStore, properties);
        return properties.build();
    }

    private Set<ModelProperty<?>> getHiddenProperties(StructSchema<T> delegateSchema, ModelSchemaStore schemaStore) {
        final ImmutableSet.Builder<ModelProperty<?>> pubPropsBuilder = ImmutableSet.builder();
        final ImmutableSet.Builder<ModelProperty<?>> intPropsBuilder = ImmutableSet.builder();
        addNonDelegatedManagedProperties(schema, delegateSchema, pubPropsBuilder);
        addInternalViewsProperties(delegateSchema, schemaStore, intPropsBuilder);
        return Sets.difference(intPropsBuilder.build(), pubPropsBuilder.build());
    }

    private void addInternalViewsProperties(StructSchema<T> delegateSchema, ModelSchemaStore schemaStore, ImmutableSet.Builder<ModelProperty<?>> properties) {
        for (ModelType<?> internalView : internalViewsFor(schema.getType())) {
            ModelSchema<?> internalViewSchema = schemaStore.getSchema(internalView);
            if (!(internalViewSchema instanceof StructSchema)) {
                continue;
            }
            addNonDelegatedManagedProperties((StructSchema<?>) internalViewSchema, delegateSchema, properties);
        }
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

    private void hideNodesOfHiddenProperties(MutableModelNode modelNode, Set<ModelProperty<?>> hiddenProps) {
        for (ModelProperty<?> hiddenProp : hiddenProps) {
            MutableModelNode hiddenPropNode = modelNode.getLink(hiddenProp.getName());
            if (hiddenPropNode != null) {
                hiddenPropNode.setHidden(true);
            }
        }
    }

    private <D> void addProjection(MutableModelNode modelNode, ModelType<?> type, StructSchema<? extends D> delegateSchema, ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, TypeConverter typeConverter) {
        ModelSchema<?> schema = schemaStore.getSchema(type);
        if (!(schema instanceof StructSchema)) {
            throw new IllegalStateException("View type must be a struct: " + type);
        }
        StructSchema<D> structSchema = Cast.uncheckedCast(schema);
        modelNode.addProjection(modelProjectionFor(structSchema, delegateSchema, proxyFactory, typeConverter));
    }

    private <D> ModelProjection modelProjectionFor(StructSchema<D> structSchema, StructSchema<? extends D> delegateSchema, ManagedProxyFactory proxyFactory, TypeConverter typeConverter) {
        return new ManagedModelProjection<D>(structSchema, delegateSchema, proxyFactory, typeConverter);
    }
}
